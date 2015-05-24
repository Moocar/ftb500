(ns me.moocar.ftb500.client.app
    (:require [reagent.core :as reagent :refer [atom]]))

(defn current-page []
  [:div "hello world!"])

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn start []
  (mount-root)
  (let [WebSocket (or (aget js/window "WebSocket")
                       (aget js/window "MozWebSocket"))
        ws (WebSocket. "ws://localhost:8080/ws")]
    (.log js/console ws)))
