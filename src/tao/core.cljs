(ns tao.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [tao.core :refer [deftao]])
  (:require [goog.events :as events]
            [clojure.string :refer [split join replace trim]]
            [cljs.core.async :refer [put! <! chan]]
            [tao.utils :refer [deep-merge-with map->params log tap]]
            [secretary.core :as secretary :refer-macros [defroute]])
  (:import goog.History
           goog.history.Html5History
           goog.history.Html5History.TokenTransformer
           goog.history.EventType))

(enable-console-print!)

(def history (atom {}))

(defn init-history
  ([] (init-history {}))
  ([{:keys [push-state]
     :or {push-state true}
     :as opts}]
     (let [hist (if push-state
                  (let [transformer (TokenTransformer.)]
                    (set! (.. transformer -retrieveToken)
                          (fn [path-prefix location]
                            (str (.-pathname location) (.-search location))))
                    (set! (.. transformer -createUrl)
                          (fn [token path-prefix location]
                            (str path-prefix token)))

                    (let [h (Html5History. js/window transformer)]
                     (.setUseFragment h false)
                     (.setPathPrefix h "")
                     (.setEnabled h true)
                     h))
                  (let [h (History.)]
                    (.setEnabled h true)
                    (secretary/set-config! :prefix "#")
                    h))
           navigation (chan)]
       (reset! history hist)
       (events/listen hist EventType.NAVIGATE #(put! navigation %))
       (go-loop []
                (when-let [e (<! navigation)]
                  (when (.-isNavigation e)
                    (secretary/dispatch! (.-token e)))
                  (recur)))
       (secretary/dispatch! (.getToken hist)))))

(defn navigate!
  ([route] (navigate! route {}))
  ([route query]
     (let [token (.getToken @history)
           old-route (first (split token "?"))
           new-route (str "/" route)
           query-string (map->params (reduce-kv (fn [valid k v]
                                                  (if v
                                                    (assoc valid k v)
                                                    valid)) {} query))
           with-params (if (empty? query-string)
                         new-route
                         (str new-route "?" query-string))]
       (if (= old-route new-route)
         (. @history (replaceToken with-params))
         (. @history (setToken with-params))))))

(def state-mappings (atom []))

(defn add-state-mapping [route translators]
  (swap! state-mappings conj [route translators]))

(defn get-path [key translators]
  (conj (get-in translators [key :path]) key))

(defn matcher->route [matcher {:keys [params query-params]}]
  (let [parts (rest (split matcher "/")) ; drop leading space
        as-keys (map #(if (re-find #":" %)
                        (keyword (replace % #":" ""))
                        %) parts)
        subbed (map #(if (keyword? %)
                       (% params)
                       %) as-keys)
        route (join "/" subbed)]
    {:route route
     :query query-params}))

(defn translate-state [[matcher {:keys [validator] :as translators}] state]
  (letfn [(translate [group]
            (reduce-kv (fn [out k {:keys [path ->route ]}]
                         (let [value (get-in state (conj path k))
                               processor (or ->route identity)
                               translated (when value
                                            (processor value))]
                           (assoc out k translated)))
                       {} group))]
    (let [translated (reduce-kv (fn [out k v]
                                  (assoc out k (translate v)))
                                {}
                                (select-keys translators [:params :query-params]))
          validator (or validator #(every? identity (vals %)))]
      (when (validator (:params translated))
        (matcher->route matcher translated)))))

(defn state->route [state]
  (let [mappings (map #(translate-state % state) @state-mappings)
        route (last (filter identity mappings))]
    route))

(defn translate-param [k translator v]
  (let [path (conj (:path translator) k)
        processor (or (:->state translator) identity)]
    (assoc-in {} path (processor v))))

(defn route->state [{:keys [params query-params constants]} route-params]
  (let [translators (merge params query-params constants)
        params-and-query (merge (dissoc route-params :query-params) (:query-params route-params))
        state (reduce-kv (fn [state k v]
                            (->> k
                                 (get params-and-query)
                                 (translate-param k v)
                                 (deep-merge-with merge state))) {} translators)]
    state))

(defn update-history
  [{:keys [path new-state tag] :as tx-data}]
  (condp = tag
    :silent nil ;ignore
    (let [{:keys [route query]} (state->route new-state)]
      (navigate! route query))))
