(ns ca.clojurist.hello
  "Port of Solana hello world example to ClojureScript."
  {:author "Robert Medeiros" :email "robert@crimeminister.org"}
  (:require
   ["path" :as path]
   ["process" :as process])
  (:require
   ["@solana/web3.js" :as web3]
   ["borsh" :as borsh])
  (:require
   [cljs.pprint :as pprint]
   [clojure.string :as cstr])
  (:require
   [promesa.core :as p])
  (:require
   [ca.clojurist.config :as config]
   [ca.clojurist.keypair :as keypair]))

;; hello_world.ts
;; -----------------------------------------------------------------------------

;; import {
;;   Keypair,
;;   Connection,
;;   PublicKey,
;;   LAMPORTS_PER_SOL,
;;   SystemProgram,
;;   TransactionInstruction,
;;   Transaction,
;;   sendAndConfirmTransaction,
;; } from '@solana/web3.js';
;; import fs from 'mz/fs';
;; import path from 'path';
;; import * as borsh from 'borsh';

;; Path to program files
(def program-path
  (path/resolve js/__dirname "../dist/program"))

;; Path to program shared object file which should be deployed on chain.
;; This file is created when running either:
;; - `npm run build:program-c`
;; - `npm run build:program-rust`
(def program-so-path
  (path/join program-path "helloworld.so"))

;; Path to the keypair of the deployed program.
;; This file is created when running `solana program deploy dist/program/helloworld.so`
(def program-keypair-path
  (path/join program-path "helloworld-keypair.json"))

;; borsh
;; -----------------------------------------------------------------------------

;; /**
;;  * The state of a greeting account managed by the hello world program
;;  */
;; class GreetingAccount {
;;   counter = 0;
;;   constructor(fields: {counter: number} | undefined = undefined) {
;;     if (fields) {
;;       this.counter = fields.counter;
;;     }
;;   }
;; }

;; The state of a greeting account managed by the hello world program.
(deftype GreetingAccount [counter])

;; /**
;;  * Borsh schema definition for greeting accounts
;;  */
;; const GreetingSchema = new Map([
;;   [GreetingAccount, {kind: 'struct', fields: [['counter', 'u32']]}],
;; ]);

;; Borsh schema definition for greeting accounts.
(def GreetingSchema
  (js/Map.
   (clj->js
    [[GreetingAccount {:kind "struct" :fields [["counter" "u32"]]}]])))

;; /**
;;  * The expected size of each greeting account.
;;  */
;; const GREETING_SIZE = borsh.serialize(
;;   GreetingSchema,
;;   new GreetingAccount(),
;; ).length;

;; The expected size of each greeting account.
;; (borsh/serialize schema value) => buffer
(def greeting-size
  (.. (borsh/serialize GreetingSchema (GreetingAccount. 0)) -length))




(defn connect&
  "Establish a connection to the cluster. Returns a promise that resolves
  to a Connection."
  [url]
  (web3/Connection. url "confirmed"))

;; /**
;;  * Establish an account to pay for everything
;;  */
;; export async function establishPayer(): Promise<void> {
;;   let fees = 0;
;;   if (!payer) {
;;     const {feeCalculator} = await connection.getRecentBlockhash();

;;     // Calculate the cost to fund the greeter account
;;     fees += await connection.getMinimumBalanceForRentExemption(GREETING_SIZE);

;;     // Calculate the cost of sending transactions
;;     fees += feeCalculator.lamportsPerSignature * 100; // wag

;;     payer = await getPayer();
;;   }

;;   let lamports = await connection.getBalance(payer.publicKey);
;;   if (lamports < fees) {
;;     // If current balance is not enough to pay for fees, request an airdrop
;;     const sig = await connection.requestAirdrop(
;;       payer.publicKey,
;;       fees - lamports,
;;     );
;;     await connection.confirmTransaction(sig);
;;     lamports = await connection.getBalance(payer.publicKey);
;;   }

;;   console.log(
;;     'Using account',
;;     payer.publicKey.toBase58(),
;;     'containing',
;;     lamports / LAMPORTS_PER_SOL,
;;     'SOL to pay for fees',
;;   );
;; }

;; TODO use getFeeForMessage() to compute fee
(defn establish-payer&
  "Establish an account to pay for everything."
  [{:keys [payer connection] :as state}]
  (let [fees 0]
    (p/let [;; We use .getLatestBlockhash() because .getRecentBlockhash() is deprecated.
            latest (.getLatestBlockhash ^Connection connection)
            ;; The returned map has two properties: blockhash (a
            ;; string), and lastValidBlockHeight (a number).
            block-hash (.-blockhash ^Object latest)
            fee-calculator (.getFeeCalculatorForBlockhash connection block-hash)
            min-balance (.getMinimumBalanceForRentExemption connection greeting-size)
            lamport-cost (* 100 (.-lamportsPerSignature ^FeeCalculator fee-calculator))
            fees (+ fees lamport-cost)
            public-key (.-publicKey payer)
            ;; The balance (in lamports) for the payer.
            balance (.getBalance connection public-key)]
      (when (< balance fees)
        ;; If current balance not enough to pay for fees, request an
        ;; airdrop.
        (println "TODO balance not enough for fees, request airdrop"))
      state)))

;; /**
;;  * Check if the hello world BPF program has been deployed
;;  */
;; export async function checkProgram(): Promise<void> {
;;   // Read program id from keypair file
;;   try {
;;     const programKeypair = await createKeypairFromFile(PROGRAM_KEYPAIR_PATH);
;;     programId = programKeypair.publicKey;
;;   } catch (err) {
;;     const errMsg = (err as Error).message;
;;     throw new Error(
;;       `Failed to read program keypair at '${PROGRAM_KEYPAIR_PATH}' due to error: ${errMsg}. Program may need to be deployed with \`solana program deploy dist/program/helloworld.so\``,
;;     );
;;   }

;;   // Check if the program has been deployed
;;   const programInfo = await connection.getAccountInfo(programId);
;;   if (programInfo === null) {
;;     if (fs.existsSync(PROGRAM_SO_PATH)) {
;;       throw new Error(
;;         'Program needs to be deployed with `solana program deploy dist/program/helloworld.so`',
;;       );
;;     } else {
;;       throw new Error('Program needs to be built and deployed');
;;     }
;;   } else if (!programInfo.executable) {
;;     throw new Error(`Program is not executable`);
;;   }
;;   console.log(`Using program ${programId.toBase58()}`);

;;   // Derive the address (public key) of a greeting account from the program so that it's easy to find later.
;;   const GREETING_SEED = 'hello';
;;   greetedPubkey = await PublicKey.createWithSeed(
;;     payer.publicKey,
;;     GREETING_SEED,
;;     programId,
;;   );

;;   // Check if the greeting account has already been created
;;   const greetedAccount = await connection.getAccountInfo(greetedPubkey);
;;   if (greetedAccount === null) {
;;     console.log(
;;       'Creating account',
;;       greetedPubkey.toBase58(),
;;       'to say hello to',
;;     );
;;     const lamports = await connection.getMinimumBalanceForRentExemption(
;;       GREETING_SIZE,
;;     );

;;     const transaction = new Transaction().add(
;;       SystemProgram.createAccountWithSeed({
;;         fromPubkey: payer.publicKey,
;;         basePubkey: payer.publicKey,
;;         seed: GREETING_SEED,
;;         newAccountPubkey: greetedPubkey,
;;         lamports,
;;         space: GREETING_SIZE,
;;         programId,
;;       }),
;;     );
;;     await sendAndConfirmTransaction(connection, transaction, [payer]);
;;   }
;; }

(defn check-program&
  "Check if the hello world BPF program has been deployed."
  [{:keys [connection payer program-id] :as state}]
  (p/let [payer-pubkey (.-publicKey payer)
          ;; Derive the address (public key) of a greeting account from
          ;; the program so that it's easy to find later.
          greeting-seed "hello"
          greeted-pubkey (web3/PublicKey.createWithSeed payer-pubkey greeting-seed program-id)
          ;; Check if the greeting account has already been created.
          greeted-account (.getAccountInfo connection greeted-pubkey)]
    (when (nil? greeted-account)
      (js/console.log "Creating account" (.toBase58 ^web3/Keypair greeted-pubkey) "to say hello to")
      ;; The greeting account has *not* been created, so prepare and
      ;; execute a transaction that will do so.
      (p/let [lamports (.getMinimumBalanceForRentExemption connection greeting-size)
              options (clj->js {:fromPubkey payer-pubkey
                                :basePubkey payer-pubkey
                                :seed greeting-seed
                                :newAccountPubkey greeted-pubkey
                                :lamports lamports
                                :space greeting-size
                                :programId program-id})
              account (web3/SystemProgram.createAccountWithSeed options)
              transaction (.add (web3/Transaction.) account)]
        ;; Send off the transaction! Returns a promise.
        (web3/sendAndConfirmTransaction connection transaction [payer])))
    (-> state
        (assoc :greeted-pubkey greeted-pubkey)
        (assoc :greeted-account greeted-account))))

;; /**
;;  * Say hello
;;  */
;; export async function sayHello(): Promise<void> {
;;   console.log('Saying hello to', greetedPubkey.toBase58());
;;   const instruction = new TransactionInstruction({
;;     keys: [{pubkey: greetedPubkey, isSigner: false, isWritable: true}],
;;     programId,
;;     data: Buffer.alloc(0), // All instructions are hellos
;;   });
;;   await sendAndConfirmTransaction(
;;     connection,
;;     new Transaction().add(instruction),
;;     [payer],
;;   );
;; }

(defn say-hello&
  ""
  [{:keys [connection greeted-pubkey program-id payer] :as state}]
  (let [data (js/Buffer.alloc 0)
        options (clj->js {:keys [{:pubkey greeted-pubkey
                                  :isSigner false
                                  :isWritable true}]
                          :programId program-id
                          :data data})
        instruction (web3/TransactionInstruction. options)
        transaction (-> (web3/Transaction.)
                        (.add instruction))]
    (-> (web3/sendAndConfirmTransaction connection transaction #js[payer])
        ;; Return the
        (p/then (constantly state)))))

;; /**
;;  * Report the number of times the greeted account has been said hello to
;;  */
;; export async function reportGreetings(): Promise<void> {
;;   const accountInfo = await connection.getAccountInfo(greetedPubkey);
;;   if (accountInfo === null) {
;;     throw 'Error: cannot find the greeted account';
;;   }
;;   const greeting = borsh.deserialize(
;;     GreetingSchema,
;;     GreetingAccount,
;;     accountInfo.data,
;;   );
;;   console.log(
;;     greetedPubkey.toBase58(),
;;     'has been greeted',
;;     greeting.counter,
;;     'time(s)',
;;   );
;; }

(defn report-greetings&
  "Return the number of times the greeted account has been said hello to."
  [{:keys [connection greeted-pubkey] :as state}]
  (p/let [account-info (.getAccountInfo connection greeted-pubkey)]
    (when (nil? account-info)
      (throw (ex-info "cannot find the greeted account" {:account greeted-pubkey})))
    (let [data (.-data account-info)
          greeting (borsh/deserialize GreetingSchema GreetingAccount data)
          counter-obj (.-counter greeting)
          counter (.-counter counter-obj)]
      (assoc state :counter counter))))

;; main
;; -----------------------------------------------------------------------------
;; We execute a promise chain where each step is a function that accepts
;; a state map and returns a promise that resolves to an updated state
;; map.

;; TEMP
;; :payer "B6uKd4DJd9p2uAbrupH1TmLfCcdwimfSMbcMit1JLfng"
;; :program "4QQNm7P4kCLfU59TvdhJZAKV5FVyqcSunJenfPdWWgqt"
;; :greeted-pubkey "ABeTGksZpKZ7LyTrqK7ZeCfrvRBxaqMBU1fGAvD8WLML"

;; /**
;;  * Connection to the network
;;  */
;; let connection: Connection;

;; /**
;;  * Keypair associated to the fees' payer
;;  */
;; let payer: Keypair;

;; /**
;;  * Hello world's program id
;;  */
;; let programId: PublicKey;

;; /**
;;  * The public key of the account we are saying hello to
;;  */
;; let greetedPubkey: PublicKey;

(defn program-id&
  [path]
  (p/let [kp (keypair/from-file& path)]
    (.-publicKey kp)))


(defn main
  [& cli-args]
  (println "Let's say hello to a Solana account...")
  (let [state {}]
    (->
     ;; Establish connection to the cluster. Note that (p/let) returns a
     ;; promise that we will chain on.
     (p/let [config (config/load&)
             url (config/rpc-url config)
             connection (connect& url)
             version (.getVersion ^Connection connection)]
       ;; TODO use timbre logging
       (println "Connection to cluster established:" url version)
       (assoc state
              :connection connection
              :config config))

     ;; Load the program ID from the program keypair (it's the
     ;; publicKey) and store in state for later usage.
     (p/then
      (fn [state]
        (p/let [program-id (program-id& program-keypair-path)]
          (js/console.log "Found program identifier:" (.toBase58 ^web3/Keypair program-id))
          (assoc state :program-id program-id))))

     ;; Determine who pays for the fees
     (p/then
      (fn [{:keys [config] :as state}]
        ;; TODO use timbre logging
        (println "Determining who pays for fees")
        (p/let [payer (config/payer& config)]
          (->> payer
               (assoc state :payer)
               establish-payer&))))

     ;; Check if the program has been deployed
     (p/then
      (fn [state]
        ;; TODO use timbre logging
        (println "Checking if program has been deployed.")
        (check-program& state)))

     ;; Say hello to an account
     (p/then
      (fn [{:keys [greeted-pubkey] :as state}]
        ;; TODO use timbre logging
        (js/console.log "Saying hello to" (.toBase58 greeted-pubkey));
        (say-hello& state)))

     ;; Find out how many times that account has been greeted
     (p/then
      (fn [{:keys [greeted-pubkey counter] :as state}]
        (p/let [state (report-greetings& state)]
          (let [greeted (.toBase58 greeted-pubkey)
                counter (get state :counter)
                message (cstr/join " " [greeted "has been greeted" counter "time(s)"])]
            (js/console.log message)
            state))))

     (p/then
      (fn []
        (js/console.log "Success")
        (process/exit)))

     (p/catch
         (fn [e]
           (js/console.error e)
           (process/exit -1))))))
