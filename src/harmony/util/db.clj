(ns harmony.util.db
  (:require [clojure.string :as str]
            [harmony.util.data :refer [map-keys map-values map-kvs]]
            [harmony.util.uuid :refer [uuid->sorted-bytes sorted-bytes->uuid]]))


;; Formatting data to and from DB
;;

(defn- camel-case-key
  "Convert a mysql column name (snake_cased) key into camelCased key."
  [k]
  (let [[f & r] (str/split (name k) #"_")]
    (keyword
     (apply str (cons f (map str/capitalize r))))))

(defn- snake-case-str
  "Convert a camelCase keyword to a snake_case string."
  [k]
  (let [[f & r] (re-seq #"^[1-9a-z]+|[A-Z][1-9a-z]*" (name k))]
    (apply str (interpose "_" (cons f (map str/lower-case r))))))

(defn- bytes-to-uuid
  "If v is a byte array representing UUID convert it to
  UUID. Otherwise return as is."
  [v]
  (if (and (instance? (Class/forName "[B") v)
           (= (count v) 16))
    (sorted-bytes->uuid v)
    v))

(defn- uuid-to-bytes
  "If v is a UUID convert to byte array. Otherwise return as is."
  [v]
  (if (instance? java.util.UUID v)
    (uuid->sorted-bytes v)
    v))

(defn- keywordize
  [k v kws]
  (if (and (contains? kws k)
           (not (keyword? v)))
    [k (keyword v)]
    [k v]))

(defn- stringify [v] (if (keyword? v) (name v) v))


(defn format-result
  "Format a raw result pulled from DB to be returned as a resource
  with camelCased keys. as-keywords is a set of key names (as
  camelCased keywords) whose value should be converted to keyword."
  ([db-response] (format-result db-response nil))
  ([db-response {:keys [as-keywords]}]
   (let [r (-> db-response
               (map-values bytes-to-uuid)
               (map-keys camel-case-key))]
     (if (seq as-keywords)
       (map-kvs r keywordize as-keywords)
       r))))

(defn format-insert-data
  "Format an object for insertion to DB by converting all UUID values
  to sorted byte arrays and by turning all keyword values to
  strings.."
  [insert-data]
  (some-> insert-data
          (map-values uuid-to-bytes)
          (map-values stringify)))

(defn format-params
  "Build a query parameters map from the given params map and column
  specification. All UUID values in parameter values are converted to
  sorted byte arrays. A column spefication is created and returned
  under key :cols using either the caller provided cols set or if that
  is omitted a default columns set. If the column spefication is
  omitted from parameters altogether then it's also omitted from
  resulting query parameters map."
  ([params] (format-params params nil))
  ([params {:keys [cols default-cols]}]
   (let [p (map-values params uuid-to-bytes)]
     (cond
       (keyword? cols)          (assoc p :cols [(snake-case-str cols)])
       (seq cols)               (assoc p :cols (map snake-case-str cols))
       (and (empty? cols)
            (seq default-cols)) (assoc p :cols (map snake-case-str default-cols))
       :else                    p))))
