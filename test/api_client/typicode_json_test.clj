(ns api-client.typicode-json-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [clj-http.client :as http]
            [api-client.typicode-json :as api-client]
            [cheshire.core :as cheshire])
  (:import [clojure.lang ExceptionInfo]
           [com.github.tomakehurst.wiremock WireMockServer]
           [com.github.tomakehurst.wiremock.client WireMock]
           [com.github.tomakehurst.wiremock.core WireMockConfiguration]))

(def port 8082)
(def base-url (str "http://localhost:" port))

(defn wiremock-fixture-generator
  [port]
  (fn wiremock-fixture
    [test-fn]
    (let [wiremock-server (doto
                            (-> (WireMockConfiguration/options)
                                (.port port)
                                (WireMockServer.))
                            (.start))]
      (try
        (test-fn)
        (finally
          ;; Clojure 1.12 specific inter op
          ;; Use (.stop ^WireMockServer wiremock-server) for earlier versions
          (WireMockServer/.stop wiremock-server))))))

(use-fixtures :once (wiremock-fixture-generator port))

(defn post-stubs
  [base-url mock-routes]
  (doseq [mock-route mock-routes]
    (http/post (str base-url "/__admin/mappings")
               {:body (cheshire/generate-string mock-route)})))

(deftest stubbed-typicode-test-take-one
  (let [test-post-id 10]
    ;; Post responses to `"http://localhost:8082/__admin/mappings"`
    ;; in order to configure responses and endpoint behaviour
    (post-stubs base-url
                [{:request
                  {:method "GET"
                   :url (str "/todos/" test-post-id)}
                  :response {:status 200
                             :body (cheshire/generate-string
                                     {:userId 10,
                                      :id 10,
                                      :title "delectus aut autem",
                                      :completed true})
                             :headers {:Content-Type "application/json"}}}
                 {:request
                  {:method "POST"
                   :url "/posts"}
                  :response {:status 200
                             :body (cheshire/generate-string
                                     {:id 101
                                      :userId 11,
                                      :title "delectus aut autem",
                                      :body "Lorem epsum"})
                             :headers {:Content-Type "application/json"}
                              :fixedDelayMilliseconds 2000
                             }}])
    (testing "works with get api"
      (let [result (api-client/get-post base-url test-post-id)]
        (is (= 200 (:status result)))
        (is (= {:completed true
                :id 10
                :title "delectus aut autem"
                :userId 10}
               (:body result)))))
    (testing "works for a 404"
      (let [result (api-client/get-post base-url 12)]
        (is (= 404 (:status result))
            "This fails using post-number `12` leads to a 404")
        (is (str/includes? (:body result)
                           "URL does not match"))))
    (time
      (testing "works for a post"
        (let [result (api-client/make-post base-url {:userId 11,
                                                     :title "delectus aut autem",
                                                     :body "Lorem epsum"})]
          (is (= 200 (:status result)))
          (is (= {:body "Lorem epsum"
                  :id 101
                  :title "delectus aut autem"
                  :userId 11} (:body result))))))))


(deftest stubbed-typicode-test-take-two
  ;; Configure WireMock client
  (^[int] WireMock/configureFor port)
  (WireMock/stubFor
    (-> (WireMock/get (WireMock/urlEqualTo (str "/todos/" 10)))
        (.willReturn
          (WireMock/okJson
            (cheshire/generate-string {:userId 10,
                                       :id 10,
                                       :title "delectus aut autem",
                                       :completed true})))))
  (WireMock/stubFor
    (-> (WireMock/post (WireMock/urlEqualTo "/posts"))
        (.willReturn
          (WireMock/okJson
            (cheshire/generate-string {:body "Lorem epsum"
                                       :id 101
                                       :title "delectus aut autem"
                                       :userId 11})))))
  (let [test-post-id 10]
    (testing "works with get api"
      (let [result (api-client/i-do-complex-stuff base-url test-post-id "Welcome!!")]
        (is (= 200 (:status result)))
        (is (= {:body "Lorem epsum"
                :id 101
                :title "delectus aut autem"
                :userId 11}
               (:body result)))))
    (testing "works for a error status"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Oooops!"
            (api-client/i-do-complex-stuff base-url 12 "Welcome!!"))
          "This fails using post-number `12` leads to a 404"))))


(deftest stubbed-typicode-test-take-three-echo
  (^[int] WireMock/configureFor port)
  (WireMock/stubFor
    (-> (WireMock/get (WireMock/urlEqualTo (str "/todos/" 10)))
        (.willReturn (WireMock/okJson (cheshire/generate-string {:userId 10,
                                                                 :id 10,
                                                                 :title "delectus aut autem",
                                                                 :completed true})))))
  (WireMock/stubFor
    (-> (WireMock/post (WireMock/urlEqualTo "/posts"))
        (.willReturn
          (-> (WireMock/aResponse)
              (.withStatus (int 200))
              (.withHeader "Content-Type" (into-array String ["application/json"]))
              (.withBody "{\"request-data\": {{jsonPath request.body '$'}},
                           \"title+description\": \"{{jsonPath request.body '$.title'}} + {{jsonPath request.body '$.body'}}\"}")
              (.withTransformers (into-array String ["response-template"]))
              #_(.withFixedDelay (int 5000))))))
  #_(time)
  (let [test-post-id 10]
    (testing "works with get api"
      (let [result (api-client/i-do-complex-stuff base-url test-post-id "Welcome!!")]
        (is (= 200 (:status result)))
        (is (= {:id "10"
                :title+description "delectus aut autem + Welcome!!"}
               (:body result)))))
    (testing "works for a 404"
      (is (thrown-with-msg?
            ExceptionInfo
            #"Oooops!"
            (api-client/i-do-complex-stuff base-url 12 "Welcome!!")
            "This fails using post-number `12` leads to a 404")))))
