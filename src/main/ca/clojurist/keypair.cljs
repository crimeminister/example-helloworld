(ns ca.clojurist.keypair
  "Work with Solana keypairs."
  {:author "Robert Medeiros" :email "robert@crimeminister.org"}
  (:require
   ["fs/promises" :as fs])
  (:require
   ["@solana/web3.js" :as web3])
  (:require
   [promesa.core :as p]))

;; from-file&
;; -----------------------------------------------------------------------------

(defn from-file&
  "Return a promise that resolves to a Keypair loaded from a secret key
  store (a file containing secret key as a byte array)."
  [file-path]
  (p/let [secret-str (fs/readFile file-path #js{:encoding "utf8"})
          secret-key (js/Uint8Array.from (js/JSON.parse secret-str))]
    (web3/Keypair.fromSecretKey secret-key)))

;; random&
;; -----------------------------------------------------------------------------

(defn random&
  "Return a promise that resolves to a new random Keypair."
  []
  (web3/Keypair.generate))
