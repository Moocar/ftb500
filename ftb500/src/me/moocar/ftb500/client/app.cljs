(ns me.moocar.ftb500.client.app
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [<!]]
            [cljs-uuid-utils.core :refer [make-random-squuid]]
            [me.moocar.ftb500.client.websocket :as websocket]
            [me.moocar.remote :as remote])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn click-take-seat [{:keys [log-ch db] :as client} seat]
  (fn [_]
    (let [response-ch (async/chan)]
      (remote/post client
                   {:route :game/join
                    :keys {:game/id (:game/id (:current-game @db))
                           :seat/id (:seat/id seat)}}
                   response-ch)
      (go
        (let [response (<! response-ch)]
          (if-let [error (:error (:request response))]
            (>! log-ch error)
            (swap! db assoc :current-seat seat)))))))

(defn render-seat
  [client seat]
  [:div
   [:h4 (:seat/position seat)]
   [:input {:type "button"
            :value "Take seat"
            :on-click (click-take-seat client seat)}]])

(defn render-game
  [{:keys [db] :as client}]
  (let [game (:current-game @db)
        {:keys [game/seats]} game]
    [:div
     [:h2 "In the game"]
     (for [seat seats]
       ^{:key seat} [:li [render-seat client seat]])]))

(defn click-add-game [{:keys [log-ch db] :as client}]
  (fn [_]
    (let [response-ch (async/chan)]
      (remote/post client
                   {:route :game/add
                    :keys {:game/id (make-random-squuid)}}
                   response-ch)
      (go
        (let [response (<! response-ch)]
          (if-let [error (:error (:request response))]
            (>! log-ch error)
            (let [game (:keys (:request response))]
              (>! log-ch response)
              (>! log-ch game)
              (remote/post client
                           {:route :game/get
                            :keys {:game/id (:game/id game)}}
                           response-ch)
              (let [game-info (:request (<! response-ch))]
                (>! log-ch "game-info")
                (>! log-ch game-info)
                (let [game (:body game-info)]
                  (swap! db (fn [db]
                              (-> db
                                  (assoc :current-game game)
                                  (assoc :lobby/current-view :game)))))))))))))

(defn render-default-lobby [client]
  (.log js/console "render defauly loggby")
  [:input {:type "button"
           :value "Add Game"
           :on-click (click-add-game client)}])

(defn render-lobby [{:keys [db] :as client}]
  (.log js/console "render lobby")
  (let [view (:lobby/current-view @db)]
    [:div
     [:h2 (str "Welcome " (:user/name (:user @db)))]
     (case view
       :game [render-game client]
       [render-default-lobby client])]))

(defn atom-input [value]
  [:input {:type "text"
           :value @value
           :on-change (fn [e]
                        (reset! value (-> e .-target .-value)))}])

(defn set-name-click [{:keys [log-ch] :as client} v]
  (fn [_]
    (let [response-ch (async/chan)]
      (.log js/console "here")
      (remote/post client
                   {:route :user/add
                    :keys {:user/id (make-random-squuid)
                           :user/name @v}}
                   response-ch)
      (.log js/console "sent to server")
      (go
        (>! log-ch (<! response-ch))))))

(defn current-page [{:keys [db] :as client}]
  (if-let [user (get-in @db [:user])]
    [render-lobby client]
    (let [name-field (atom "enter here")]
      [:div
       [:h2 "What is your name?"]
       [atom-input name-field]
       [:input {:type "button"
                :value "Set name"
                :on-click (set-name-click client name-field)}]])))

(defn mount-root [client]
  (reagent/render [current-page client] (.getElementById js/document "app")))

(defn start-client
  [client]
  (let [{:keys [server-chans route-app]} client
        mult (async/mult (:recv-ch server-chans))
        route-tap (async/chan)
        route-pub (async/pub route-tap (fn [event]
                                         (async/put! (:log-ch client) {:got (:request event)})
                                         (:route (:request event))))]
    (async/tap mult route-tap)
    (assoc client :mult mult :route-tap route-tap :route-pub route-pub)))

(defn on-user-add [client]
  (let [{:keys [route-pub db]} client
        ch (async/chan)]
    (async/sub route-pub :user/add ch)
    (go-loop []
      (when-let [user (:keys (:request (<! ch)))]
        (.log js/console (str user))
        (swap! db assoc :user user)
        (.log js/console (str @db))
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
    (go-loop []
      (when-let [msg (<! log-ch)]
        (.log js/console (str msg))
        (recur)))
    (mount-root client)
    (.log js/console "mounted")))
