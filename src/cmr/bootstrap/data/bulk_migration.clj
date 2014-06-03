(ns cmr.bootstrap.data.bulk-migration
  (:require [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [cmr.common.config :as config]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [sqlingvo.core :as sql :refer [sql select insert from where with order-by desc delete as]]
            [sqlingvo.vendor :as v]
            [cmr.metadata-db.data.oracle.sql-utils :as su]
            [clojure.core.async :as ca :refer [thread alts!! <!!]]
            [cmr.oracle.connection :as oc]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]))

;; To copy a provider
;; 1. Tell the metadata db to drop the provider
;; 2. Tell the metadata db to create the provider
;; 3. Insert collections by selecting from dataset table.
;; 4. Iterate over dataset ids for provider and insert granules into metadata db table
;; by selecting from catlaog-rest table.

(def catalog-rest-tablespace (config/config-value-fn :catalog-rest-tablespace "DEV_52_CATALOG_REST"))

(def metadata-db-tablespace (config/config-value-fn :metadata-db-tablespace "METADATA_DB"))

(def metadata-db-url (config/config-value-fn :metadata-db-host "http://localhost:3001"))

(defn catalog-rest-dataset-table
  "Get the dataset table name for a given provider."
  [provider-id]
  (keyword (str (catalog-rest-tablespace) "." provider-id "-dataset-records")))

(defn catalog-rest-granule-table
  "Get the granule table name for a given provider."
  [provider-id]
  (keyword (str (catalog-rest-tablespace) "." provider-id "-granule-records")))

(defn metadata-db-concept-table
  "Get the collection/granule table name for a given provider."
  [provider-id concept-type]
  (keyword (str (metadata-db-tablespace) "." (tables/get-table-name provider-id concept-type))))

(defn create-provider-url
  []
  (format "%s/providers" (metadata-db-url)))

(defn delete-provider-url
  [provider-id]
  (format "%s/providers/%s" (metadata-db-url) provider-id))

(defn- create-provider
  "Create the provider with the given provider id"
  [provider-id]
  (client/post (create-provider-url)
               {:body (format "{\"provider-id\": \"%s\"}" provider-id)
                :content-type :json}))

(defn- delete-provider
  "Delete the provider with the matching provider-id from the CMR metadata repo."
  [provider-id]
  (client/delete (delete-provider-url provider-id)
                 {:throw-exceptions false}))

(defn- delete-collection-sql
  "Generate SQL to delete a collection from a provider's collection table."
  [provider-id collection-id]
  (let [collection-table (tables/get-table-name provider-id :collection)]
    (su/build (delete collection-table (where `(= :concept-id ~collection-id))))))

(defn- delete-collection
  "Delete a collection from a provider's collection table."
  [db provider-id collection-id]
  (info "Deleting collection" collection-id "from provider" provider-id)
  (j/with-db-transaction
    [conn db]
    (j/execute! conn (delete-collection-sql provider-id collection-id))))

(defn- delete-collection-granules-sql
  "Generate SQL to delete granules for a given collection from a provider's granule table."
  [provider-id collection-id]
  (let [granule-table (tables/get-table-name provider-id :granule)]
    (su/build (delete granule-table (where `(= :parent-collection-id ~collection-id))))))

(defn- delete-collection-granules
  "Delete granules for a given collection from a provider's granule table."
  [db provider-id collection-id]
  (info "Deleting granules for collection" collection-id)
  (j/with-db-transaction
    [conn db]
    (j/execute! conn (delete-collection-granules-sql provider-id collection-id))))

(defn- get-dataset-record-id-for-collection-sql
  "Generate SQL to retrieve the id for a given collection/dataset from the catalog-rest table."
  [provider-id collection-id]
  (su/build (select [:id] (from (catalog-rest-dataset-table provider-id))
                    (where `(= :echo-collection-id ~collection-id)))))

(defn- get-dataset-record-id-for-collection
  "Retrieve the id for a given collection/dataset from the catalog-rest table."
  [db provider-id collection-id]
  (:id (j/with-db-transaction
         [conn db]
         (j/query conn (get-dataset-record-id-for-collection-sql provider-id collection-id)))))


(defn- get-provider-collection-list-sql
  "Gengerate SQL to get the list of collections (datasets) for the given provider."
  [provider-id]
  (su/build (select [:id :echo-collection-id] (from (catalog-rest-dataset-table provider-id)))))

(defn- get-provider-collection-list
  "Get the list of collections (datasets) for the given provider."
  [db provider-id]
  (j/with-db-transaction
    [conn db]
    (j/query conn (get-provider-collection-list-sql provider-id))))

(defn- copy-collection-data-sql
  "Generate SQL to copy the dataset/collection data from the catalog rest database to the
  metadata db for the given provider for all the provider's collections or a single collection."
  ([provider-id] (copy-collection-data-sql provider-id nil))
  ([provider-id  collection-id] (let [dataset-table (catalog-rest-dataset-table provider-id)
                                      collection-table (metadata-db-concept-table provider-id
                                                                                  :collection)]
                                  (su/build (insert collection-table [:concept-id
                                                                      :native-id
                                                                      :metadata
                                                                      :format
                                                                      :short-name
                                                                      :version-id
                                                                      :entry-title]
                                                    (select [:echo-collection-id
                                                             :dataset-id
                                                             :compressed-xml
                                                             :xml-mime-type
                                                             :short-name
                                                             :version-id
                                                             :long-name]
                                                            (from dataset-table)
                                                            (when collection-id
                                                              (where `(= :echo-collection-id ~collection-id)))))))))


(defn- copy-collection-data
  "Copy the dataset/collection data from the catalog rest database to the metadata db
  for the given provider."
  ([db provider-id] (copy-collection-data db provider-id nil))
  ([db provider-id collection-id] (info "Copying collection data for provider" provider-id)
   (j/with-db-transaction
     [conn db]
     (j/execute! conn (copy-collection-data-sql provider-id collection-id)))))

(defn- copy-granule-data-for-collection-sql
  "Generate the SQL to copy the granule data from the catalog reset datbase to the metadata db."
  [provider-id collection-id dataset-record-id]
  (let [granule-echo-table (catalog-rest-granule-table provider-id)
        granule-mdb-table (metadata-db-concept-table provider-id :granule)]
    (su/build (insert granule-mdb-table [:concept-id
                                         :native-id
                                         :parent-collection-id
                                         :metadata
                                         :format]
                      (select [:echo-granule-id
                               :granule-ur
                               collection-id
                               :compressed-xml
                               :xml-mime-type]
                              (from granule-echo-table)
                              (where `(= :dataset-record-id ~dataset-record-id)))))))

(defn- copy-granule-data-for-collection
  "Copy the granule data from the catalog reset datbase to the metadata db."
  [db provider-id collection-id dataset-record-id]
  (let [stmt (copy-granule-data-for-collection-sql provider-id collection-id dataset-record-id)]
    (j/with-db-transaction
      [conn db]
      (j/execute! conn stmt))))

(defn- copy-granule-data-for-provider
  "Copy the granule data for every collection for a given provider."
  [db provider-id]
  (info "Copying granule data for provider" provider-id)
  (doseq [{:keys [id echo_collection_id]} (get-provider-collection-list db provider-id)]
    (copy-granule-data-for-collection db provider-id echo_collection_id id)))

(defn copy-single-collection
  "Delete a collection form the provider's collection table and all associated granules and
  then copy the data from the catalog-rest db."
  [db provider-id collection-id]
  (let [dataset-record-id (get-dataset-record-id-for-collection db provider-id collection-id)]
    (delete-collection-granules db provider-id collection-id)
    (delete-collection db provider-id collection-id)
    (copy-collection-data db provider-id collection-id)
    (copy-granule-data-for-collection db provider-id collection-id dataset-record-id)
    (info "Processing of collection" collection-id "for provider" provider-id "completed.")))


(defn copy-provider
  "Copy all data for a given provider (including datasets and granules from the catalog-rest
  database into the metadata db database."
  [db provider-id]
  (delete-provider provider-id)
  (create-provider provider-id)
  (copy-collection-data db provider-id)
  (copy-granule-data-for-provider db provider-id)
  (info "Processing of provider" provider-id "completed."))

;; Background task to handle requests
(defn handle-copy-requests
  "Handle any requests for copying data from echo catalog rest to metadata db."
  [system]
  (info "Starting background task for monitoring bulk migration channels.")
  (let [db (:db system)
        channels ((juxt :provider-channel :collection-channel) system)] ; add other channels as needed
    (thread (while true
              (try ; catch any errors and log them, but don't let the thread die
                (let [[v ch] (alts!! channels)]
                  (cond
                    ;; add other channels as needed
                    (= (:provider-channel system) ch)
                    (do
                      (info "Processing provider" v)
                      (copy-provider db v))

                    (= (:collection-channel system) ch)
                    (let [{:keys [provider-id collection-id]} v]
                      (info "Processing collection" collection-id "for provider" provider-id)
                      (copy-single-collection db provider-id collection-id))))
                (catch Throwable e
                  (error (.getMessage e))))))))


(comment
  (delete-provider "FIX_PROV1")
  (get-provider-collection-list (oc/create-db (oc/db-spec)) "FIX_PROV1")
  (copy-provider (oc/create-db (oc/db-spec)) "FIX_PROV1")
  (copy-single-collection (oc/create-db (oc/db-spec)) "FIX_PROV1" "C1000000073-FIX_PROV1")
  (get-provider-collection-list-sql  "FIX_PROV1")
  (copy-granule-data-for-provider (oc/create-db (oc/db-spec)) "FIX_PROV1")
  (delete-collection-granules-sql "FIX_PROV1" "C1000000073-FIX_PROV1")
  (metadata-db-concept-table "FIX_PROV1" :collection)
  )
