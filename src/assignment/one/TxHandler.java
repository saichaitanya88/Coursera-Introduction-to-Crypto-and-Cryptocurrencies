package assignment.one;

import java.io.Console;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
        ArrayList<UTXO> utxos = utxoPool.getAllUTXO();
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        for (int i = 0; i < inputs.size(); i++){
            Transaction.Input input = inputs.get(i);
            UTXO txUTXO = new UTXO(input.prevTxHash, input.outputIndex);
            long utxoClaimCount = utxos.stream().filter(pUTXO -> pUTXO.equals(txUTXO)).count();
            if (utxoClaimCount > 1)
                return false;
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
        ArrayList<Transaction> allValidTransactions = new ArrayList<Transaction>();

        for(Transaction transaction: possibleTxs){
            if(isValidTx(transaction)){
                allValidTransactions.add(transaction);
            }
        }

        ArrayList<Transaction> validTransactions = filterDoubleSpends(allValidTransactions);
        return validTransactions.toArray(new Transaction[validTransactions.size()]);
    }

    public ArrayList<Transaction> filterDoubleSpends(ArrayList<Transaction> txs){
        Set<UTXO> transactionUTXOs = new HashSet<UTXO>();
        ArrayList<Transaction> nonDoubleSpendTransactions = new ArrayList<Transaction>();

        for(Transaction transaction : txs){
            boolean transactionContainsDoubleSpend = false;
            // transactions utxo's should not belong in the hashset
            for(Transaction.Input input : transaction.getInputs()){
                transactionContainsDoubleSpend = transactionContainsDoubleSpend || transactionUTXOs.contains(new UTXO(input.prevTxHash, input.outputIndex));
            }
            if (!transactionContainsDoubleSpend){
                nonDoubleSpendTransactions.add(transaction);
                transaction.getInputs().forEach(input -> transactionUTXOs.add(new UTXO(input.prevTxHash, input.outputIndex)));
            }
        }

        ArrayList<UTXO> allUTXOs = new ArrayList<UTXO>();

        for(Transaction transaction : nonDoubleSpendTransactions){
            for(Transaction.Input input: transaction.getInputs()){
                allUTXOs.add(new UTXO(input.prevTxHash, input.outputIndex));
            }
        }

        ArrayList<Transaction> transactionsWithValidUTXOClaims = new ArrayList<Transaction>();
        for(Transaction transaction: nonDoubleSpendTransactions){
            boolean containsMultipleUTXOClaims = false;
            for(Transaction.Input input: transaction.getInputs()) {
                containsMultipleUTXOClaims = containsMultipleUTXOClaims || allUTXOs.stream()
                        .filter(utxo -> utxo.equals(new UTXO(input.prevTxHash, input.outputIndex)))
                        .count() > 1;
            }
            if (!containsMultipleUTXOClaims)
                transactionsWithValidUTXOClaims.add(transaction);
        }

        return transactionsWithValidUTXOClaims;
    }
}