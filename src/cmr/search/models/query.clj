(ns cmr.search.models.query
  "Defines various query models and conditions."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.parameter-parser :as pp]
            [clojure.string :as s]))

(def default-page-size 10)
(def default-page-num 1)

(defrecord Query
  [
   ;; The concept type that is being queried.
   concept-type

   ;; The root level condition
   condition

   ;; the desired number of results
   page-size

   ;; the desired page in the result set - starting at zero
   page-num

   ;; A sequence of maps with :order and :field for sorting
   sort-keys
   ])

(defrecord ConditionGroup
  [
   ;; The operation combining the conditions i.e. :and or :or
   operation

   ;; A sequence of conditions in the group
   conditions
   ])

(defrecord NestedCondition
  [
   ;; The path for the nested query
   path

   ;; The nested condition
   condition
   ])

(defrecord StringCondition
  [
   ;; The field being searched.
   field

   ;; The value to match
   value

   ;; indicates if the search is case sensitive. Defaults to true.
   case-sensitive?

   ;; Indicates if the search contains pattern matching expressions. Defaults to false.
   pattern?
   ])

(defrecord NegatedCondition
  [
   ;; condition to exclude
   condition
   ])

(defrecord BooleanCondition
  [
   ;; The field being searched.
   field

   ;; The boolean value to match
   value
   ])

(defrecord SpatialCondition
  [
   ;; One of cmr.spatial polygon, line, point, or mbr
   shape
   ])

;; Allows execution of a custom native search script
(defrecord ScriptCondition
  [
   ;; name of the script
   script

   ;; Parameter map of names to values
   params
   ])

;; ExistCondition represents the specified field must have value, i.e. filed is not null
(defrecord ExistCondition
  [
   ;; The field being searched.
   field
   ])

;; MissingCondition represents the specified field must not have value, i.e. filed is nil
(defrecord MissingCondition
  [
   ;; The field being searched.
   field
   ])

(defrecord DateValueCondition
  [
   ;; The field being searched
   field

   ;; The date value
   value
   ])

(defrecord DateRangeCondition
  [
   ;; The field being searched.
   field

   ;; The start-date value
   start-date

   ;; The end-date value
   end-date
   ])

(defrecord NumericValueCondition
  [
   ;; The field being searched
   field

   ;; The value to match.
   value
   ])

(defrecord NumericRangeCondition
  [
   ;; The field being searched.
   field

   ;; The minimum value (inclusive)
   min-value

   ;; Them maximum value (inclusive)
   max-value
   ])

(defrecord StringRangeCondition
  [
   ;; The field being searched
   field

   ;; The start value for the range
   start-value

   ;; The end value for the range
   end-value
   ])

(defrecord TemporalCondition
  [
   ;; The field being searched.
   field

   ;; The date range condition
   date-range-condition

   ;; The start-day value
   start-day

   ;; The end-day value
   end-day
   ])

(defrecord OrbitNumberValueCondition
  [
   value
   ])

(defrecord OrbitNumberRangeCondition
  [
   min-value
   max-value
   ])

(defrecord EquatorCrossingLongitudeCondition
  [
   min-value
   max-value
   ])

(defrecord EquatorCrossingDateCondition
  [
   start-date
   end-date
   ])

(defrecord CollectionQueryCondition
  [
   ;; The condition to find collections
   condition
   ])

(defrecord MatchAllCondition
  [])

(defrecord MatchNoneCondition
  [])

(def attribute-types
  "A list of valid additional attribute search types"
  [:float :int :string :date :time :datetime])

(defrecord AttributeValueCondition
  [
   type
   name
   value
   ])

(defrecord AttributeRangeCondition
  [
   type
   name
   min-value
   max-value
   ])

(def default-sort-keys
  "The default sort keys by concept type."
  {:collection [{:field :entry-title
                 :order :asc}]

   :granule [{:field :provider-id
              :order :asc}
             {:field :start-date
              :order :asc}]})

(defn query
  "Constructs a query with the given type, page-size, page-num,
  and root condition. If root condition is not provided it matches everything.
  If page-size or page-num are not specified then they are given default values."
  [params]
  (let [{:keys [concept-type page-size page-num condition sort-keys]} params
        page-size (or page-size default-page-size)
        page-num (or page-num default-page-num)
        condition (or condition (->MatchAllCondition))
        sort-keys (or sort-keys (default-sort-keys concept-type))]
    (->Query concept-type condition page-size page-num sort-keys)))

(defn numeric-value-condition
  "Creates a NumericValueCondition"
  [field value]
  (map->NumericValueCondition {:field field :value value}))

(defn numeric-range-condition
  [field min max]
  (map->NumericRangeCondition {:field field
                               :min-value min
                               :max-value max}))

(defn string-condition
  "Creates a string condition."
  ([field value]
   (string-condition field value false false))
  ([field value case-sensitive? pattern?]
   (->StringCondition field value case-sensitive? pattern?)))

(defn string-range-condition
  "Create a string range condition."
  [field start stop]
  (map->StringRangeCondition {:field field :start-value start :end-value stop}))

(defn date-range-condition
  "Creates a DateRangeCondition."
  [field start stop]
  (map->DateRangeCondition {:field field
                            :start-date start
                            :end-date stop}))
(defn date-value-condition
  "Creates a DateValueCondtion."
  [field value]
  (->DateValueCondition field value))

(defn nested-condition
  "Creates a nested condition."
  [path condition]
  (->NestedCondition path condition))

(defn group-conds
  "Combines the conditions together in the specified type of group."
  [type conditions]
  (cond
    (empty? conditions) (errors/internal-error! "Grouping empty list of conditions")
    (= (count conditions) 1) (first conditions)
    :else (->ConditionGroup type conditions)))

(defn and-conds
  "Combines conditions in an AND condition."
  [conditions]
  (group-conds :and conditions))

(defn or-conds
  "Combines conditions in an OR condition."
  [conditions]
  (group-conds :or conditions))

(defn numeric-range-str->condition
  "Creates a numeric range condition."
  [field value]
  (let [{:keys [min-value max-value]} (pp/numeric-range-parameter->map value)]
    (->NumericRangeCondition field min-value max-value)))
