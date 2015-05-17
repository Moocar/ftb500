(ns me.moocar.ftb500.server.routes.add-user
  (:require [clojure.core.async :as async :refer [<! >! go]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]))
{:msg
 {:route :user/add,
  :keys
  {:user/name "9a6239b4-d80e-4f68-8f5b-000f32fc06ae",
   :user/id #uuid "6fd7b4ff-1767-4a4b-8824-19e2bad9c710"}}}

(defn process
  [this]
  (let [{:keys [tx-ch]} (:datomic-conn this)]
    (fn [context result-ch]
      (let [{:keys [msg send-ch session-id]} context
            {user-id :user/id user-name :user/name} (:keys msg)
            tx [{:db/id #db/id[:db.part/session]
                 :session/id session-id
                 :session.user/id user-id}
                {:db/id #db/id[:db.part/ftb500]
                 :user/id user-id
                 :user/name user-name}]
            response-ch (async/chan)]
        (go
          (>! tx-ch [tx response-ch])
          (<! response-ch)
          (>! send-ch msg)
          (async/close! result-ch))))))

(defrecord AddUser [;; Dependencies
                    server log-ch datomic-conn
                    ;; After started
                    ch]
  component/Lifecycle
  (start [this]
    (let [{:keys [route-pub-ch]} server
          ch (async/chan)]
      (async/sub route-pub-ch :user/add ch)
      (async/pipeline-async 5 (async/chan (async/dropping-buffer 1))
                            (process this)
                            ch)
      (assoc this :ch ch)))
  (stop [this]
    (let [{:keys [route-pub-ch]} server]
      (async/close! ch)
      (async/unsub route-pub-ch :game/add ch)
      (assoc this :ch nil))))

(defn construct []
  (component/using (map->AddUser {})
    {:server :me.moocar.ftb500/server
     :log-ch :log-ch
     :datomic-conn :datomic-conn}))
