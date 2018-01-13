package assignment.one;

import java.security.PublicKey;
import java.util.*;

public class MaxFeeTxHandler {

    private UTXOPool utxoPool;

    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = utxoPool;
    }

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

        // TODO: find optimal fees rather than taking the greedy approach.
        // Perhaps processing linked transactions yields a greater fee than individual transactions.

        return sortTransactionsByFeesGreedy(validTransactions);
    }

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


    public Transaction[] sortTransactionsByFeesGreedy(ArrayList<Transaction> transactions){
        HashMap<Double, ArrayList<Transaction>> transactionFeeMap = new HashMap<>();
        for(Transaction transaction : transactions){
            double fees = getTransactionFees(transaction);
            ArrayList<Transaction> txs = transactionFeeMap.getOrDefault(fees, new ArrayList<Transaction>());
            txs.add(transaction);
            transactionFeeMap.put(fees, txs);
        }
        ArrayList<Transaction> sortedTransactions = new ArrayList<>();

        transactionFeeMap.keySet().stream().sorted()
                .forEach(
                        f -> transactionFeeMap.get(f).forEach(tx -> sortedTransactions.add(tx))
                );
        return sortedTransactions.toArray(new Transaction[sortedTransactions.size()]);
    }

    public Transaction[] sortTransactionsByFeesOptimal(ArrayList<Transaction> transactions) throws Exception{
        // Find transactions in the block that are connected to each other
        // Add linked transactions into a bucket
        // Decide if processing all linked transactions yields in greater fees
        throw new Exception("Not Implemented");
    }

    public double getTransactionFees(Transaction tx){
        ArrayList<UTXO> pUTXOs = utxoPool.getAllUTXO();
        double inputSum = tx.getInputs().stream()
                .filter(input -> pUTXOs.contains(new UTXO(input.prevTxHash, input.outputIndex)))
                .map(input -> new UTXO(input.prevTxHash, input.outputIndex))
                .mapToDouble(utxo -> utxoPool.getTxOutput(utxo).value).sum();
        double outputSum = tx.getOutputs().stream().mapToDouble(o -> o.value).sum();
        return inputSum - outputSum;
    }

    public Transaction getTransactionByHash(ArrayList<Transaction> transactions, byte[] hash){
        for(Transaction tx: transactions){
            if (tx.getHash() == hash) return tx;
        }
        return null;
    }

    public class TransactionSet{
        public ArrayList<Transaction> transactions;
        public double fees;
        public double getFees(){
            return fees;
        }
        public TransactionSet(){
            this.transactions = new ArrayList<>();
        }
    }
}