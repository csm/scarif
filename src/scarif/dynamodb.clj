(ns scarif.dynamodb
  (:refer-clojure :exclude [assoc! get])
  (:require [clojure.edn :as edn])
  (:import [com.amazonaws.services.dynamodbv2 AmazonDynamoDBClient]
           [com.netflix.config.sources DynamoDbConfigurationSource]
           [com.netflix.config FixedDelayPollingScheduler DynamicConfiguration ConfigurationManager ConcurrentCompositeConfiguration DynamicURLConfiguration DynamicPropertyFactory]
           [com.amazonaws.regions Region Regions]
           [org.apache.commons.configuration SystemConfiguration EnvironmentConfiguration]
           [clojure.lang IDeref]
           [com.amazonaws.services.dynamodbv2.model GetItemRequest AttributeValue GetItemResult PutItemRequest UpdateItemRequest AttributeValueUpdate AttributeAction]
           [com.amazonaws AmazonServiceException]))

(defn init!
  "Initialize Archaius for dynamodb polling.

  Optional keys:

  :region -- The AWS region to connect to. Defaults to us-west-2.
  :poll-frequency -- The frequency to poll dynamodb for config changes, in milliseconds. Defaults to 60000.
  :url-configuration? -- Whether to install a DynamicURLConfiguration in the config chain (true).
  :system-configuration? -- Whether to install a SystemConfiguration in the config chain (true).
  :env-configuration? -- Whether to install a EnvironmentConfiguration in the config chain (true)."
  [& {:keys [region poll-frequency url-configuration? system-configuration? env-configuration?]
      :or {region "us-west-2"
           poll-frequency 60000
           url-configuration? true
           system-configuration? true
           env-configuration? true}}]
  (let [ddb (doto (AmazonDynamoDBClient.)
              (.setRegion (Region/getRegion (Regions/fromName region))))
        source (DynamoDbConfigurationSource. ddb)
        scheduler (FixedDelayPollingScheduler. 0 poll-frequency false)
        dynConfig (DynamicConfiguration. source scheduler)
        finalConfig (ConcurrentCompositeConfiguration.)]
    (.addConfiguration ^ConcurrentCompositeConfiguration finalConfig dynConfig "dynamodb")
    (when url-configuration?
      (.addConfiguration finalConfig (DynamicURLConfiguration.) "url"))
    (when system-configuration?
      (.addConfiguration finalConfig (SystemConfiguration.) "system"))
    (when env-configuration?
      (.addConfiguration finalConfig (EnvironmentConfiguration.) "env"))
    (if (ConfigurationManager/isConfigurationInstalled)
      (ConfigurationManager/loadPropertiesFromConfiguration finalConfig)
      (ConfigurationManager/install finalConfig))))

(def default-table-name
  (reify IDeref
    (deref [_]
      (.. (DynamicPropertyFactory/getInstance)
          (getStringProperty "com.netflix.config.dynamo.tableName" "archaiusProperties")
          (getValue)))))

(def default-key-attribute-name
  (reify IDeref
    (deref [_]
      (.. (DynamicPropertyFactory/getInstance)
          (getStringProperty "com.netflix.config.dynamo.keyAttributeName" "key")
          (getValue)))))

(def default-value-attribute-name
  (reify IDeref
    (deref [_]
      (.. (DynamicPropertyFactory/getInstance)
          (getStringProperty "com.netflix.config.dynamo.valueAttributeName" "value")
          (getValue)))))

(defn ^String named-to-str
  [n]
  (if (or (keyword? n) (symbol? n))
    (str (when-let [ns (namespace n)] (str ns \/)) (name n))
    n))

(defn get
  "Get the current value of k in dynamodb."
  [{:keys [table-name key-attribute-name value-attribute-name ddb-client region]
    :or {table-name @default-table-name
         key-attribute-name @default-key-attribute-name
         value-attribute-name @default-value-attribute-name
         region "us-west-2"}}
   k]
  (let [ddb-client (or ddb-client (doto (AmazonDynamoDBClient.)
                                    (.setRegion (Region/getRegion (Regions/fromName region)))))
        k (named-to-str k)
        response (.getItem ddb-client (GetItemRequest. table-name {key-attribute-name (AttributeValue. k)}))]
    (some-> response
            (.getItem)
            (clojure.core/get value-attribute-name)
            (.getS)
            (edn/read-string))))

(defn update!
  "Update an entry in dynamodb. Returns new value."
  [{:keys [table-name key-attribute-name value-attribute-name ddb-client region]
    :or {table-name @default-table-name
         key-attribute-name @default-key-attribute-name
         value-attribute-name @default-value-attribute-name
         region "us-west-2"}}
   k f & args]
  (loop []
    (let [ddb-client (or ddb-client (doto (AmazonDynamoDBClient.)
                                      (.setRegion (Region/getRegion (Regions/fromName region)))))
          k (named-to-str k)
          response (.getItem ddb-client (GetItemRequest. table-name {key-attribute-name (AttributeValue. k)}))
          current-raw-value ^AttributeValue (clojure.core/get (.getItem response) value-attribute-name)
          current-value (some-> current-raw-value
                                (.getS)
                                (edn/read-string))
          new-value (apply f current-value args)
          put-response (try
                         (if (nil? current-raw-value)
                           (.putItem ddb-client (doto (PutItemRequest. table-name {key-attribute-name (AttributeValue. k)
                                                                                   value-attribute-name (pr-str new-value)})
                                                  (.setConditionExpression "attribute_not_exists(#k)")
                                                  (.setExpressionAttributeNames {"#k" key-attribute-name})))
                           (.updateItem ddb-client (doto (UpdateItemRequest.)
                                                     (.setTableName table-name)
                                                     (.setKey {key-attribute-name (AttributeValue. k)})
                                                     (.setUpdateExpression "SET #v = :newv")
                                                     (.setConditionExpression "#v = :oldv")
                                                     (.setExpressionAttributeNames {"#v" value-attribute-name})
                                                     (.setExpressionAttributeValues {":oldv" current-raw-value
                                                                                     ":newv" (AttributeValue. (pr-str new-value))})))))]
      (cond (and (instance? AmazonServiceException put-response)
                 (= "ConditionalCheckFailedException" (.getErrorCode put-response)))
            (recur)

            (instance? Throwable put-response)
            (throw put-response)

            :else
            new-value))))

(defn assoc!
  [spec k v]
  (update! spec k (constantly v)))