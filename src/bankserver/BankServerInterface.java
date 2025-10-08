package bankserver;

import common.AccountState;
import common.GroupInfo;
import common.Message;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BankServerInterface extends Remote {

    // --- Transactions ---
    String deposit(String currency, double amount) throws RemoteException;
    String addInterest(String currency, double percent) throws RemoteException;
    String addInterestAll(double percent) throws RemoteException;
    String getSyncedBalance(String currency) throws RemoteException;
    double getQuickBalance(String currency) throws RemoteException;

    // --- History / status ---
    void getHistory() throws RemoteException;
    void cleanHistory() throws RemoteException;
    void checkTxStatus(String txId) throws RemoteException;

    // --- Membership ---
    void printMembers() throws RemoteException;
    void updateMembership(GroupInfo groupInfo) throws RemoteException;

    // --- Messaging from MDServer ---
    void receiveMessage(Message msg) throws RemoteException;
    void ack(String messageId) throws RemoteException;

    // --- Identification ---
    String getServerName() throws RemoteException;

    // --- State Transfer ---
    AccountState getAccountState() throws RemoteException;
}
