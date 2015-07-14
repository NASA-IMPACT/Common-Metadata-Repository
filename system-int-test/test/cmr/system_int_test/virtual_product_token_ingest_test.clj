(ns cmr.system-int-test.virtual-product-token-ingest-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.virtual-product-util :as vp]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.system :as s]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.common.time-keeper :as tk]
            [clj-time.core :as t]))

(use-fixtures :each (ingest/reset-fixture (into {} (for [p vp/virtual-product-providers]
                                                     [(str p "_guid") p])) true false))

(defn assert-matching-granule-urs
  "Asserts that the references found from a search match the expected granule URs."
  [expected-granule-urs {:keys [refs]}]
  (is (= (set expected-granule-urs)
         (set (map :name refs)))))

(defn ingest-virtual-collections
  "Ingests the virtual collections for the given set of source collections."
  [source-collections options]
  (->> source-collections
       (mapcat vp/virtual-collections)
       (mapv #(d/ingest (:provider-id %) % options))))

(deftest ingest-with-system-token-test
  (e/grant-group-provider-admin (s/context) "prov-admin-update-group-guid" "LPDAAC_ECS_guid" :update)
  ;; Grant ingest permission on LPDAAC_ECS to the group to which mock echo system token belongs
  (e/grant-group-provider-admin (s/context) "mock-admin-group-guid" "LPDAAC_ECS_guid" :update)

  (let [provider-admin-update-token (e/login (s/context) "prov-admin-update"
                                             ["prov-admin-update-group-guid"])
        token-option {:token provider-admin-update-token}
        ast-coll (d/ingest "LPDAAC_ECS"
                           (dc/collection
                             {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"})
                           token-option)
        vp-colls (ingest-virtual-collections [ast-coll] token-option)
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (d/ingest "LPDAAC_ECS" (dg/granule ast-coll
                                                        {:granule-ur granule-ur}) token-option)
        expected-granule-urs (vp/source-granule->virtual-granule-urs ast-l1a-gran)
        all-expected-granule-urs (cons (:granule-ur ast-l1a-gran) expected-granule-urs)]
    (index/wait-until-indexed)
    (assert-matching-granule-urs
      all-expected-granule-urs
      (search/find-refs :granule {:page-size 50}))))

