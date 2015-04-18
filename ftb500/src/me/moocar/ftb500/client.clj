(ns me.moocar.ftb500.client
  (:require [clojure.core.async :as async :refer [go-loop <! >!]]
            [com.stuartsierra.component :as component]
            [me.moocar.remote :refer [idempotent-get]]))

(defn msg-topic-fn
  [msg]
  (select-keys (:msg msg) [:route :keys]))

(defrecord PubClient [;; dependencies
                      server-chans

                      ;; after start
                      pub-ch pub-tap mult]
  component/Lifecycle
  (start [this]
    (if pub-ch
      this
      (let [mult (async/mult (:recv-ch server-chans))
            pub-tap (async/chan 1)
            pub-ch (async/pub pub-tap msg-topic-fn)]
        (async/tap mult pub-tap)
        (assoc this
               :pub-ch pub-ch
               :pub-tap pub-tap
               :mult mult))))
  (stop [this]
    (if pub-ch
      (do (async/unsub-all pub-ch)
          (async/untap mult pub-tap)
          (assoc this :pub-ch nil :pub-tap nil :mult nil))
      this)))

(defn log-tap
  [client]
  (let [ch (async/chan 1)]
    (async/tap (:mult client) ch)
    (go-loop []
      (when-let [msg (:msg (<! ch))]
        (>! (:log-ch client) {:cli-recv msg})
        (recur)))))

(defn new-pub-client [config server-chans]
  (component/using
    (map->PubClient {:server-chans server-chans})
    [:log-ch]))

(defn add-game-request [game-id]
  {:route :game/add
   :keys {:game/id game-id}})

(defn add-game
  [client game-id ch]
  (let [request (add-game-request game-id)]
    (idempotent-get client request ch)))

(defn get-game
  [client game-id ch]
  (let [request {:route :game/get
                 :keys {:game/id game-id}}]
    (idempotent-get client request ch)))

(defn joined? [response]
  (not (:error response)))

(defn join-game
  [client game-id seat-id ch]
  (let [request {:route :game/join
                 :keys {:game/id game-id
                        :seat/id seat-id}}]
    (idempotent-get client request ch)))
