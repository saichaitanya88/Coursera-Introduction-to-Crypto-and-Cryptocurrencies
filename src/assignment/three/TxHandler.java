package assignment.three;

import java.io.Console;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Stream;

public class TxHandler {

    private UTXOPool utxoPool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = utxoPool;
    }

    public UTXOPool getUTXOPool() {
        return utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        if (tx == null) return false;
        boolean allClaimedOutputsInCurrentPool = areAllClaimedOutputsInCurrentPool(tx);
        boolean inputSignaturesAreValid = areInputSignaturesValid(tx);
        boolean noUTXOClaimedMultipleTimes = areNoUTXOClaimedMultipleTimes(tx);
        boolean allTXOutputsAreNonNegative = areAllTXOutputsNonNegative(tx);
        boolean sumOfInputsGreaterThanSumOfOutputs = isSumOfInputsGreaterThanSumOfOutputs(tx);

        return allClaimedOutputsInCurrentPool && inputSignaturesAreValid &&
                noUTXOClaimedMultipleTimes && allTXOutputsAreNonNegative &&
                sumOfInputsGreaterThanSumOfOutputs;
    }

    public boolean areAllClaimedOutputsInCurrentPool(Transaction tx){
        boolean valid = true;
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        for(int i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);
            ArrayList<UTXO> utxos = utxoPool.getAllUTXO();
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            valid = valid && utxos.contains(utxo);
        }
        return valid;
    }

    public boolean areInputSignaturesValid(Transaction tx){
        boolean valid = true;
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        for(int i = 0; i < inputs.size(); i++) {
            Transaction.Input input = inputs.get(i);
            if (input == null) return false;

            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            Transaction.Output output = utxoPool.getTxOutput(utxo);
            if (output == null) return false;

            PublicKey pubkey = output.address;
            byte[] rawDataToSign = getRawDataToSign(tx, i);
            valid = valid && verifySignature(pubkey, rawDataToSign, input);
        }
        return valid;
    }

    public byte[] getRawDataToSign(Transaction tx, int i){
        return tx.getRawDataToSign(i);
    }

    public boolean verifySignature(PublicKey pubkey, byte[] dataToSign, Transaction.Input input){
        return Crypto.verifySignature(pubkey, dataToSign, input.signature);
    }

    public boolean areNoUTXOClaimedMultipleTimes(Transaction tx){
        ArrayList<UTXO> utxos = new ArrayList<UTXO>();
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        for (int i = 0; i < inputs.size(); i++){
            Transaction.Input input = inputs.get(i);
            UTXO txUTXO = new UTXO(input.prevTxHash, input.outputIndex);
            if (utxos.contains(txUTXO)) return false;
            utxos.add(txUTXO);
        }
        return true;
    }

    public boolean areAllTXOutputsNonNegative(Transaction tx){
        return tx.getOutputs().stream().allMatch(o -> o.value >= 0);
    }

    public boolean isSumOfInputsGreaterThanSumOfOutputs(Transaction tx){
        ArrayList<UTXO> pUTXOs = utxoPool.getAllUTXO();
        double inputSum = tx.getInputs().stream()
                .filter(input -> pUTXOs.contains(new UTXO(input.prevTxHash, input.outputIndex)))
                .map(input -> new UTXO(input.prevTxHash, input.outputIndex))
                .mapToDouble(utxo -> utxoPool.getTxOutput(utxo).value).sum();
        double outputSum = tx.getOutputs().stream().mapToDouble(o -> o.value).sum();
        return inputSum >= outputSum;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        if (possibleTxs == null) return new Transaction[0];

        ArrayList<Transaction> validTransactions = new ArrayList<>();
        for(Transaction transaction: possibleTxs){
            if (isValidTx(transaction)){
                // remove all inputs from unspent transaction outputs
                for(Transaction.Input input : transaction.getInputs())
                    this.utxoPool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));

                byte[] txHash = transaction.getHash();
                // add inputs into unspent transaction outputs
                for(int i = 0; i < transaction.getOutputs().size(); i++){
                    UTXO utxo = new UTXO(txHash, i);
                    utxoPool.addUTXO(utxo, transaction.getOutput(i));
                }
                validTransactions.add(transaction);
            }
        }
        return validTransactions.toArray(new Transaction[validTransactions.size()]);
    }
}