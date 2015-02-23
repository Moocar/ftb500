(ns me.moocar.ftb500)

;; request
{:route :add-game
 :keys {:game/id 1}}

{:route :add-game
 :keys {:game/id 1}}

;; response
{:route :add-game
 :keys {:game/id 1}
 :error {:class :server
         :mitigate :retry
         :retry-ms 1000
         :name :no-more-space}}

{:route :add-game
 :keys {:game/id 1}
 :error {:class :system
         :name :game-id-not-uuid}}

(defn idempotent-wait-for-confirmation
  [this request]
  (let [{:keys [server]} this
        {:keys [pub-ch send-ch reconnect-mult error-ch]} server
        response-ch (async/chan 1)
        reconnect-ch (async/ch)]
    (try
      (async/tap reconnect-mult reconnect-ch)
      (async/sub pub-ch request response-ch)
      (go-loop []
        (>! send-ch request)
        (alt! response-ch
              ([response]
               (when response
                 (if-let [error (:error response)]
                   (if (retryable? error)
                     (do (<! (async/timeout (:retry-ms error)))
                         (recur))
                     (if (= :system (:class error))
                       (>! error-ch response)
                       response))
                   response)))

              ;; If a reconnect is detected, then send the request again
              reconnect-ch
              ([v]
               (when v
                 (recur)))

              ;; if a timeout occurs, then try to send the request again
              (async/timeout 1000)
              ([v]
               (when v
                 (recur)))))
      (finally
        (async/untap conn-mult conn-ch)
        (async/unsub pub-ch (request= request) response-ch)))))

(defn inline-connect
  [client server]
  )

(defn inline-server
  "Recv-ch receives requests from clients. The message contains the route, body and connection"
  []
  (let [recv-ch ()]))

(defn new-server-client-conn
  [server]
  (let [send-ch (async/chan 1)
        recv-ch (async/chan 1)]
    ;; (map #(assoc %request :send-ch send-ch))
    {:send-ch send-ch
     :recv-ch recv-ch}))

(defn start-server-client-conn
  [server server-client-conn]
  (admix (:mix server) (:recv-ch server-client-conn)))

(defn stop-server-client-conn
  [server server-client-conn]
  (unmix (:mix server) (:recv-ch server-client-conn)))

(defn new-inline-server []
  (go-loop []
    (when-let [msg (<! recv-ch)]

      (if (= :disconnect msg)
        (do (unmix mix (:recv-ch (:conn request)))
            ())))))

;; both

(defn new-conn []
  {:send-ch (async/chan 1)
   :recv-ch (async/chan 1)})

;; client
(defn new-client
  []
  (let [conn (new-conn)]
    {:conn conn}))

(defn inline-connect
  [client server]
  (let [server-conn (new-conn)]
    (async/pipe (:send-ch (:conn client)) (:recv-ch conn))
    (async/pipe (:send-ch conn) (:recv-ch (:conn client)))
    (new-connection server conn)))

#_(defn inline-disconnect
  [client server]
  (close-connection! server conn))

;; server connections
(defn new-connection
  [server conn]
  (go-loop []
    (when-let [msg (<! (:recv-ch conn))]
      (>! (:recv-ch server) (assoc msg :conn conn))
      (recur)))
  (swap! (:conns server) conj conn)
  server)

(defn close-connection!
  [server conn]
  (async/close! (:recv-ch conn))
  (async/close! (:send-ch conn))
  (swap! (:conns server) disj conn))

;; Server
(defn new-server []
  {:conns (atom #{})
   :recv-ch (async/chan 1)})

(defn handler [everything msg]
  (let [{:keys [route body conn]} msg]
    (comment "do something")
    nil))

(defn start-server [server]
  (go-loop []
    (when-let [msg (<! (:recv-ch server))]
      (handle this msg)))
  server)

(defn stop-server
  [server]
  (doseq [conn @(:conns server)]
    (close-connection! server conn))
  server)

(defn inline-connect
  [client server]
  (let [server-client-conn (new-server-client-conn server)
        ch (async/chan 1 (map (fn [msg] (assoc msg :conn server-client-conn))))]
    (async/pipe (:recv-ch server-client-conn) ch)
    (admix (:mix server) ch)
    (start-server-client-conn)
    (async/pipe (:send-ch client) (:recv-ch server-client-conn))
    (async/pipe (:send-ch server-client-conn) (:recv-ch client))))

(defn inline-disconnect
  [client server]
  (go (>! (:send-ch client))))

(defn new-inline-client
  [server]
  (let [send-ch (async/chan 1)
        recv-ch ()]))



#_(defn add-game
  "unfinished"
  [this]
  (let [{:keys [device-ch]} this
        add-game-ch (async/chan 1)]
    (async/sub (:pub-ch device-ch) :add-game add-game-ch)
    (go-loop []
      (when-let [msg (<! add-game-ch)]
        (let [game-id (:game/id (:keys msg))
              request {:route :add-game
                       :keys {:game/id game-id}}
              response (<! (idempotent-wait-for-confirmation this request))]
          (if-let [error (:error response)]
            (do (>! (:send-ch device-ch) error)
                (recur))
            ()))))))

#_(defn join-game
  [this]
  )
