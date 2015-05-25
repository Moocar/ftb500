(ns me.moocar.ftb500.client.app
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [<!]]
            [cljs-uuid-utils.core :refer [make-random-squuid]]
            [me.moocar.ftb500.client.websocket :as websocket]
            [me.moocar.remote :as remote])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

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
                           :user/name @val}}
                   response-ch)
      (.log js/console "sent to server")
      (go
        (.log js/console (str (<! response-ch)))))))

(defn render-lobby [{:keys [db] :as client}]
  [:h2 (str "Welcome " (:user/name (:user @db)))])

(defn current-page [client]
  (if-let [user (get-in @(:db client) [:user])]
    (render-lobby client)
    (let [name-field (atom "enter here")]
      [:div
       [:h2 "What is your name?"]
       [atom-input name-field]
       [:input {:type "button"
                :value "Set name"
                :on-click (set-name-click client val)}]])))

(defn mount-root [client]
  (reagent/render [current-page client] (.getElementById js/document "app")))

(defn start-client
  [client]
  (let [{:keys [server-chans route-app]} client
        mult (async/mult (:recv-ch server-chans))
        route-tap (async/chan)
        route-pub (async/pub route-tap (comp :route :request))]
    (async/tap mult route-tap)
    (assoc client :mult mult :route-tap route-tap :route-pub route-pub)))

(defn on-user-add [client]
  (let [{:keys [route-pub db]} client
        ch (async/chan)]
    (async/sub route-pub :user/add ch)
    (go-loop []
      (when-let [user (:keys (:request (<! ch)))]
        (swap! db assoc :user user)
        (recur)))))

(defn start-handlers [client]
  (on-user-add client))

(defn start []
  (.log js/console "mounted")
  (let [transport-chans {:send-ch (async/chan)
                         :recv-ch (async/chan)}
        log-ch (async/chan)
        client {:server-chans transport-chans
                :log-ch log-ch
                :db (atom {})}
        client (start-client client)
        ws {:uri "ws://localhost:8080/ws"
            :transport-chans transport-chans
            :log-ch log-ch}
        ws (websocket/start ws)]
    (start-handlers client)
    (go
      (loop []
        (when-let [msg (<! log-ch)]
          (.log js/console msg)
          (recur))))
    (mount-root client)
    (.log js/console "mounted")))
