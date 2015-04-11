(ns me.moocar.remote
  (:require [clojure.core.async :as async :refer [<! >! alt! go-loop go]]))

(defn retryable?
  [request]
  (= :retry (get-in request [:error :mitigate])))

(defn idempotent-wait-for-confirmation
  [client request]
  (let [{:keys [pub-ch server-chans error-ch]} client
        {:keys [send-ch]} server-chans
        response-ch (async/chan 1)
                                        ;        reconnect-ch (async/chan)
        ]
    (go
      (try
                                        ;     (async/tap reconnect-mult reconnect-ch)
        (async/sub pub-ch request response-ch)
        (prn "request" request client)
        (<!
         (go-loop []
           (>! send-ch request)
           (alt! response-ch
                 ([response]
                  (println "got a response" response)
                  (when response
                    (if-let [error (:error response)]
                      (if (retryable? error)
                        (do (<! (async/timeout (:retry-ms error)))
                            (recur))
                        (if (= :system (:class error))
                          (>! error-ch response)
                          response))
                      response)))

                 ;; ;; If a reconnect is detected, then send the request again
                 ;; reconnect-ch
                 ;; ([v]
                 ;;  (when v
                 ;;    (recur)))

                 ;; if a timeout occurs, then try to send the request again
                 (async/timeout 1000)
                 ([v]
                  (println "timeout")
                  (when v
                    (recur))))))
        (finally
                                        ;        (async/untap conn-mult conn-ch)
          (async/unsub pub-ch request response-ch))))))
