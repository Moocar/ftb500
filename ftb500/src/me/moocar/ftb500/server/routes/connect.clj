(ns me.moocar.ftb500.server.routes.connect
  (:require [clojure.core.async :as async :refer [<! >! go]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]))
(defn process
  [this]
  (let [{:keys [conn]} (:datomic-conn this)]
    (fn [context result-ch]
      (let [{:keys [msg send-ch session-id]} context
            db (d/db conn)]
        (go
          (try
            (when-let [session (d/entity db [:session/id session-id])]
              (let [user (d/pull db '[:session.user/id :session.user/name] (:db/id session))]
                (when (:session.user/id user)
                  (>! send-ch {:route :user/add
                               :keys {:user/name (:session.user/name user)
                                      :user/id (:session.user/id user)}}))))
            (finally
              (async/close! result-ch))))))))

(defrecord Connect [;; Configuration
                    sub-k
                    ;; Dependencies
                    server log-ch datomic-conn
                    ;; After started
                    ch]
  component/Lifecycle
  (start [this]
    (let [{:keys [route-pub-ch]} server
          ch (async/chan)]
      (async/sub route-pub-ch sub-k ch)
      (async/pipeline-async 5 (async/chan (async/dropping-buffer 1))
                            (process this)
                            ch)
      (assoc this :ch ch)))
  (stop [this]
    (let [{:keys [route-pub-ch]} server]
      (async/close! ch)
      (async/unsub route-pub-ch sub-k ch)
      (assoc this :ch nil))))

(defn construct []
  (component/using (map->Connect {:sub-k :me.moocar.websocket/connect})
    {:server :me.moocar.ftb500/server
     :log-ch :log-ch
     :datomic-conn :datomic-conn}))
