(ns api-client.typicode-json
  (:require
    [cheshire.core :as cheshire]
    [clj-http.client :as client]))

(defn get-post
  [base-url post-number]
  (client/get (str base-url "/todos/" post-number)
              {:as :json
               :throw-exceptions false}))

(defn make-post
  [base-url post-data]
  (client/post (str base-url "/posts")
              {:as :json
               :body (cheshire/generate-string post-data)
               :throw-exceptions false}))

(defn i-do-complex-stuff
  [base-url post-number post-description]
  (let [{:keys [status body]} (get-post base-url post-number)]
    (if (not= 200 status)
      (throw (ex-info "Oooops!" {:reason status :response body}))
      (->> (assoc body :body post-description)
           (make-post base-url)))))

(comment
  (api-client/get-post "https://jsonplaceholder.typicode.com" 1)

  )
