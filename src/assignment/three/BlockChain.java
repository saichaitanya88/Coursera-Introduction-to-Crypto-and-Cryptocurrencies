//package assignment.three;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory
// as it would cause a memory overflow.

import java.util.ArrayList;
import java.util.HashMap;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
    // Acts like an in-memory database
    private HashMap<byte[], BlockNode> blockChain;
    // Reference to the latest node in the blockchain
    private BlockNode maxHeightNode;
    private TransactionPool transactionPool;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        blockChain = new HashMap<>();
        UTXOPool pool = new UTXOPool();
        addCoinbaseToUTXOPool(genesisBlock, pool);
        BlockNode genesisNode = new BlockNode(genesisBlock, null, pool);
        blockChain.put(genesisBlock.getHash(), genesisNode);
        transactionPool = new TransactionPool();
        maxHeightNode = genesisNode;
    }

    public void addCoinbaseToUTXOPool(Block block, UTXOPool pool){
        Transaction coinbase = block.getCoinbase();
        for(int i = 0; i < coinbase.numOutputs(); i++){
            UTXO utxo = new UTXO(coinbase.getHash(), i);
            pool.addUTXO(utxo, coinbase.getOutput(i));
        }
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightNode.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightNode.getUtxoPool();
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     *
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     *
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        byte[] previousBlockHash = block.getPrevBlockHash();
        if (previousBlockHash == null) return false;

        BlockNode parentBlockNode = blockChain.get(previousBlockHash);
        if (parentBlockNode == null) return false;

        TxHandler txHandler = new TxHandler(parentBlockNode.getUtxoPool());
        ArrayList<Transaction> transactions = block.getTransactions();
        Transaction[] validTransactions = txHandler.handleTxs(transactions.toArray(new Transaction[transactions.size()]));

        // do not mine the block unless all transactions are valid
        if (validTransactions.length != transactions.size()) return false;

        // do not mine if the block is not past the cutoff age
        int nextBlockHeight = parentBlockNode.height + 1;
        if (nextBlockHeight <= maxHeightNode.height - CUT_OFF_AGE) return false;

        UTXOPool utxoPool = txHandler.getUTXOPool();
        addCoinbaseToUTXOPool(block, utxoPool);
        BlockNode node = new BlockNode(block, parentBlockNode, utxoPool);
        blockChain.put(block.getHash(), node);

        // change the reference to the maxHeightNode
        if (nextBlockHeight > maxHeightNode.height){
            maxHeightNode = node;
        }

        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        transactionPool.addTransaction(tx);
    }

    private class BlockNode{
        public Block block;
        public BlockNode parent;
        public int height;
        public UTXOPool utxoPool;
        public BlockNode(Block b, BlockNode bn, UTXOPool p){
            this.block = b;
            this.parent = bn;
            this.utxoPool = p;
            if (parent == null) height = 1;
            else{
                height = this.parent.height + 1;
            }
        }
        public UTXOPool getUtxoPool(){
            return new UTXOPool(utxoPool);
        }
    }
}