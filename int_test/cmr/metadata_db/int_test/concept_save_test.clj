(ns cmr.metadata-db.int-test.concept-save-test
  "Contains integration tests for saving concepts. Tests saves with various configurations including
  checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each util/reset-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest mdb-save-concept-test
  "Save a valid concept with no revision-id."
  (let [concept (util/concept)
        {:keys [status revision-id]} (util/save-concept (util/concept))]
    (is (and (= status 201) (= revision-id 0)))
    (util/verify-concept-was-saved concept 0)))

(deftest mdb-save-concept-test-with-proper-revision-id-test
  "Save a valid concept with a valid revision-id"
  (let [concept (util/concept)]
    ;; save the concept once
    (util/save-concept concept)
    ;; save it again with a valid revision-id
    (let [{:keys [status revision-id]} (util/save-concept (assoc concept :revision-id 1))]
      (is (and (= status 201) (= revision-id 1)))
      (util/verify-concept-was-saved concept 1))))

(deftest mdb-save-concept-with-bad-revision-test
  "Fail to save a concept with an invalid revision-id"
  (let [concept (util/concept)
        _ (util/save-concept concept)
        concept-with-bad-revision (assoc concept :revision-id 2)
        {:keys [status]} (util/save-concept concept-with-bad-revision)
        {:keys [retrieved-concept]} (util/get-concept-by-id (:concept-id concept))
        retrieved-revision (:revision-id retrieved-concept)]
    (is (and (= status 409) (nil? retrieved-revision)))))

(deftest mdb-save-concept-with-missing-required-parameter
  "Fail to save a concept if a required parameter is missing"
  (testing "missing concept-type"
    (let [{:keys [status error-messages]} (util/save-concept (dissoc (util/concept) :concept-type))]
      (is (and (= 422 status) (re-find #"concept-type" (first error-messages))))))
  (testing "missing concept-id"
    (let [{:keys [status error-messages]} (util/save-concept (dissoc (util/concept) :concept-id))]
      (is (and (= 422 status) (re-find #"concept-id" (first error-messages)))))))

(deftest mdb-save-concept-after-delete
  "Verify that a save after delete returns the correct revision."
  (let [concept (util/concept)]
    (util/save-concept concept)
    (util/delete-concept (:concept-id concept))
    (let [{:keys [status revision-id]} (util/save-concept concept)]
      (is (= revision-id 2)))))

;;; This test is disabled because the middleware is currently returning a
;;; 500 status code instead of a 400. This will be addressed as a separate
;;; issue.
#_(deftest mdb-save-concept-with-invalid-json-test
  "Fail to save a concept if the json is invalid"
  (let [response (client/post "http://localhost:3000/concepts" 
                              {:body "some non-json"
                               :body-encoding "UTF-8"
                               :content-type :json
                               :accept :json
                               :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        error-messages (get body "errors")]
    (is (= status 400))))
