(ns konfo-backend.contentful.updater-test
  (:require [clojure.test :refer :all]
            [konfo-backend.contentful.updater-api :refer :all]
            [ring.mock.request :as mock]))

(intern 'clj-log.access-log 'service "konfo-backend-updater")

(deftest updater-test
  (testing "Healthcheck API test"
    (let [response (updater-app (mock/request :get "/konfo-backend-updater/healthcheck"))]
      (is (= (:status response) 200))))

  (testing "Unauthorized access"
    (let [response (updater-app (mock/request :post "/konfo-backend-updater/update"))]
      (is (= (:status response) 401))))

  (testing "Start update"
    (let [response (updater-app
                     (mock/header
                       (mock/request :post "/konfo-backend-updater/update")
                       "Authorization"
                       "Basic b3BoOm9waA=="))]
      (is (= (:status response) 200)))))