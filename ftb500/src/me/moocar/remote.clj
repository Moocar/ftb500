(ns me.moocar.remote
  (:require [clojure.core.async :as async :refer [<! >! alt! go-loop go]]
            [me.moocar.lang :refer [uuid]]))

(defn retryable?
  [request]
  (= :retry (get-in request [:error :mitigate])))

(defn idempotent-get
  [client request response-ch]
  (let [{:keys [pub-ch server-chans error-ch]} client
        {:keys [send-ch]} server-chans
        keyed-request (select-keys request [:route :keys])]
    (async/sub pub-ch keyed-request response-ch)
    (go
      (>! (:log-ch client) {:idempotent-get request})
      (try
        (loop []
          (async/put! send-ch request)
          (alt! response-ch
                ([response]
                 #_(>! (:log-ch client) {:response response})
                 (when response
                   (if-let [error (:error response)]
                     (if (retryable? error)
                       (do (<! (async/timeout (:retry-ms error)))
                           (recur))
                       (if (= :system (:class error))
                         (async/put! error-ch response)
                         (async/put! response-ch response)))
                     (async/put! response-ch response))))))
        (finally
          (async/unsub pub-ch keyed-request response-ch)))))
  response-ch)

(defn post
  [client request response-ch]
  (let [{:keys [server-chans error-ch mult]} client
        {:keys [send-ch]} server-chans
        request-tap (async/chan 1)
        pub-ch (async/pub request-tap (comp :request/id :request))
        request-id (str (uuid))
        request (assoc request :request/id request-id)]
    (async/tap mult request-tap)
    (async/sub pub-ch request-id response-ch)
    (go
      (try
        (loop []
          (async/put! send-ch request)
          (alt! response-ch
                ([response]
                 (when response
                   (if-let [error (:error response)]
                     (if (retryable? error)
                       (do (<! (async/timeout (:retry-ms error)))
                           (recur))
                       (if (= :system (:class error))
                         (async/put! error-ch response)
                         (async/put! response-ch response)))
                     (async/put! response-ch response))))))
        (finally
          (async/unsub pub-ch request-id response-ch)
          (async/untap mult request-tap)))))
  response-ch)
