(ns konfo-backend.eperuste.eperuste
  (:require [konfo-backend.index.eperuste :as eperuste-index]
            [konfo-backend.index.osaamisalakuvaus :as osaamisalakuvaus-index]))

(defn get-eperuste-by-id
  [id]
  (eperuste-index/get id))

(defn get-osaamisalakuvaus-by-id
  [id]
  (osaamisalakuvaus-index/get id))

(defn get-kuvaus-by-koulutuskoodi-uri
  [koulutuskoodi-uri with-osaamisalakuvaukset?]
  (let [kuvaus (eperuste-index/get-kuvaus-by-koulutuskoodi koulutuskoodi-uri)
        id     (:id kuvaus)]
    (cond-> kuvaus
            (and with-osaamisalakuvaukset? (some? id)) (assoc :osaamisalat (osaamisalakuvaus-index/get-kuvaukset-by-eperuste-id id)))))