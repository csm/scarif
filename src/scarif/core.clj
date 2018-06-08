(ns scarif.core
  (:require [clojure.spec.alpha :as s]
            [clojure.edn :as edn])
  (:import [com.netflix.config DynamicPropertyFactory DynamicStringProperty]))

(defn ^:no-doc var-symbol
  "Returns the namespace-qualified symbol for the var."
  [v]
  (symbol (-> v meta :ns ns-name name)
          (-> v meta :name name)))

(defn ^:no-doc set-value!
  [v prop-ref]
  (let [^DynamicStringProperty prop @prop-ref
        new-value (edn/read-string (.getValue prop))]
    (when-let [spec (s/get-spec (keyword (.getName prop)))]
      (when-let [result (s/explain-data spec new-value)]
        (throw (ex-info (str "value for " (.getName prop) " did not conform to spec: " (s/explain spec new-value))
                        {:explain-data result}))))
    (alter-var-root v (constantly new-value))))

(defn ^:no-doc change-listener
  [v prop-ref cb]
  (fn []
    (println "change-listener firing" v prop-ref cb)
    (let [^DynamicStringProperty prop @prop-ref
          old-value @v
          new-value (edn/read-string (.getValue prop))]
      (set-value! v prop-ref)
      (when (some? cb)
        (try (cb old-value new-value)
             (catch Throwable _))))))

(defmacro defconfig
  "Define a configuration variable with name NAME, backed by Archaius.

  This will define a var with the given name, initialized to the given default value. The value will be
  read as a DynamicStringProperty, and will assume the value is encoded with EDN.

  If you define a spec with the same name (e.g. if your var is user/foo and you define spec `:user/foo`)
  then the value read from Archaius will be validated against that spec before altering the value of
  the var.

  You can pass in the optional metadata:

  :on-change -- For dynamic properties, a function that is called when the value of the property changes.
                The callback should take two arguments: the old value and the new value.

  The new var will have the following metadata added to it:

  :property -- The DynamicStringProperty instance."
  ([name]
   `(defconfig ~name nil))
  ([name default-val]
   `(let [var# (def ~name ~default-val)
          qname# (var-symbol var#)
          cb# (-> var# meta :on-change)
          prop-ref# (ref nil)
          prop# (.. DynamicPropertyFactory getInstance (getStringProperty (str qname#) (pr-str ~default-val) (change-listener var# prop-ref# cb#)))]
      (dosync
        (ref-set prop-ref# prop#))
      (set-value! var# prop-ref#)
      (alter-meta! var# assoc :property prop#)
      var#)))
