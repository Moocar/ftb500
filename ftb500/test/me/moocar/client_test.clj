(ns me.moocar.client-test
  (:require [clojure.core.async :as async :refer [go >!! <!! <! >! go-loop alts!!]]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [me.moocar.component :refer [with-test-system with-test-systems]]
            [me.moocar.ftb500.client :as client]
            [me.moocar.lang :refer [uuid deep-merge]]
            [me.moocar.remote :refer [idempotent-get]]
            [me.moocar.websocket.full-system :as full-system]))

(defn log-loop
  [components]
  (let [log-chans (map :log-ch components)]
    (go-loop []
      (let [[v p] (async/alts! log-chans)]
        (prn (:system v) (dissoc v :system))
        (recur)))))

(defn local-config
  []
  {:server {:datomic {;; Generate a new db-name each time due to the
                      ;; tranactor needing 1-minute to delete
                      ;; database:
                      ;; https://groups.google.com/forum/#!msg/datomic/1WBgM84nKmc/UzhyugWk6loJ
                      :db-name (str (uuid))
                      :sub-uri "datomic:free://localhost:4334"
                      :reset-db? true
                      :create-database? true
                      :create-schema? true}
            :websocket {:hostname "localhost"
                        :port :random}}})

(defn update-config [config server-system]
  (assoc-in config [:server :websocket :port]
            (:local-port (:me.moocar.websocket/server server-system))))

(defn t-add-user [config server-system]
  (with-test-system [client-system (full-system/new-client-system config)]
    (let [{client :me.moocar.ftb500/client
           log-ch :log-ch} client-system
           user-id (uuid)
           player-name (str (uuid))
           user-id-request (client/add-user-request user-id player-name)
           response-ch (async/chan 1)]
      (idempotent-get client user-id-request response-ch)
      (is (alts!! [response-ch (async/timeout 1000)])))))

#_(defn t-should-send-user-info-when-connected
  "Connnect to a server and set your self up as a user. Then
  disconnect, and reconnect. The server should then push your user
  information down to you"
  [config server-system]
  (let [client-system (component/start (full-system/new-client-system config))
        {ftb500-client :me.moocar.ftb500/client
         websocket-client :me.moocar.websocket/client} client-system
        user-id (uuid)
        player-name (str (uuid))
        response-ch (async/chan)
        user-id-ch (async/chan)
        user-id-request (client/add-user-request user-id player-name)]
    (idempotent-get ftb500-client user-id-request response-ch)
    (alts!! [response-ch (async/timeout 1000)])
    (async/sub (:pub-ch ftb500-client)
               user-id-request
               user-id-ch)
    (component/start (component/stop websocket-client))
    (is (first (alts!! [user-id-ch (async/timeout 1000)])))))

#_(defn t-joined [config server-system]
  (with-test-systems [client-systems 2 (full-system/new-client-system config)]
    (let [[client1 client2] (map :me.moocar.ftb500/client client-systems)
          game-id (uuid)
          response-ch (async/chan 1)]
      (client/add-game client1 game-id response-ch)
      (<!! response-ch)
      (client/get-game client1 game-id response-ch)
      (let [game-info (<!! response-ch)
            seats (get-in game-info [:msg :body :seats])]
        (client/join-game client1 game-id (first seats) response-ch)
        (client/join-game client2 game-id (second seats) response-ch)))))

(deftest t-with-server
  (let [config (local-config)]
    (with-test-system [server-system (full-system/new-server-system config)]
      (let [config (update-config config server-system)]
        (t-add-user config server-system)
        #_(t-should-send-user-info-when-connected config server-system)))))
