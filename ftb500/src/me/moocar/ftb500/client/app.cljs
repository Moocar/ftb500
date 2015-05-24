(ns me.moocar.ftb500.client.app
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [<!]]
            [cljs-uuid-utils.core :refer [make-random-squuid]]
            [me.moocar.ftb500.client.websocket :as websocket]
            [me.moocar.remote :as remote])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn atom-input [value]
  [:input {:type "text"
           :value @value
           :on-change (fn [e]
                        (reset! value (-> e .-target .-value)))}])

(defn set-name-click [client val]
  (fn [_]
    (let [response-ch (async/chan)]
      (remote/post client
                   {:route :user/add
                    :keys {:user/id (make-random-squuid)
                           :user/name @val
                           }}
                   response-ch)
      (.log js/console "sent to server")
      (go
        (.log js/console (str (<! response-ch)))))))

(defn current-page [client]
  (let [val (atom "enter here")]
    [:div
     [:h2 "What is your name?"]
     [atom-input val]
     [:input {:type "button"
              :value "Set name"
              :on-click (set-name-click client val)}]]))

(defn mount-root [client]
  (reagent/render [current-page client] (.getElementById js/document "app")))

(defn start-client
  [this]
  (let [{:keys [server-chans]} this
        mult (async/mult (:recv-ch server-chans))]
    (assoc this :mult mult)))

(defn start []
  (.log js/console "mounted")
  (let [transport-chans {:send-ch (async/chan)
                         :recv-ch (async/chan)}
        log-ch (async/chan)
        client {:server-chans transport-chans
                :log-ch log-ch}
        client (start-client client)
        ws {:uri "ws://localhost:8080/ws"
            :transport-chans transport-chans
            :log-ch log-ch}
        ws (websocket/start ws)]
    (go
      (loop []
        (when-let [msg (<! log-ch)]
          (.log js/console msg)
          (recur))))
    (mount-root client)
    (.log js/console "mounted")))
