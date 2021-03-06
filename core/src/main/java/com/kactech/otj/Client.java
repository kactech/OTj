/*******************************************************************************
 *              OTj
 * Low-level client-side library for Open Transactions in Java
 * 
 * Copyright (C) 2013 by Piotr Kopeć (kactech)
 * 
 * EMAIL: pepe.kopec@gmail.com
 * 
 * BITCOIN: 1ESADvST7ubsFce7aEi2B6c6E2tYd4mHQp
 * 
 * OFFICIAL PROJECT PAGE: https://github.com/kactech/OTj
 * 
 * -------------------------------------------------------
 * 
 * LICENSE:
 * This program is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero
 * General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * ADDITIONAL PERMISSION under the GNU Affero GPL version 3
 * section 7: If you modify this Program, or
 * any covered work, by linking or combining it with other
 * code, such other code is not for that reason alone subject
 * to any of the requirements of the GNU Affero GPL version 3.
 * (==> This means if you are only using the OTj, then you
 * don't have to open-source your code--only your changes to
 * OTj itself must be open source. Similar to
 * LGPLv3, except it applies to software-as-a-service, not
 * just to distributing binaries.)
 * Anyone using my library is given additional permission
 * to link their software with any BSD-licensed code.
 * 
 * -----------------------------------------------------
 * 
 * You should have received a copy of the GNU Affero General
 * Public License along with this program. If not, see:
 * http://www.gnu.org/licenses/
 * 
 * If you would like to use this software outside of the free
 * software license, please contact Piotr Kopeć.
 * 
 * DISCLAIMER:
 * This program is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU Affero General Public License for
 * more details.
 ******************************************************************************/
package com.kactech.otj;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kactech.otj.model.UserAccount;

public class Client implements Closeable {
	static final Logger logger = LoggerFactory.getLogger(Client.class);
	public static boolean DEBUG_JSON = false;
	public static boolean DEBUG_RAW = false;
	UserAccount userAccount;
	String serverID;
	String serverNymID;
	PublicKey serverPublicKey;
	Transport transport;

	ReqNumManager reqNumManager;

	public String send(String unsigned) {
		try {
			String signed = Utils.sign(unsigned, userAccount.getCpairs().get("A").getPrivate());
			logger.debug('\n' + signed);
			return Utils.parseSigned(send_s(signed)).getUnsigned();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 
	 * @param signed
	 * @return SIGNED string
	 */
	private String send_s(String signed) {

		byte[] bytes;
		try {
			bytes = Utils.sealToB64(signed, serverNymID, serverPublicKey);
		} catch (Exception e) {
			throw new RuntimeException("sealing message", e);
		}
		bytes = transport.send(bytes);
		if (bytes == null)
			throw new NoResponseException();
		String str = Utils.string(bytes, Utils.US_ASCII);
		if (str.contains("ENVELOPE")) {
			str = Utils.unarmor(str, false);
			byte[] by = Utils.base64Decode(str);
			try {
				by = Utils.unpack(by, byte[].class);
				str = Utils.open(by, userAccount.getCpairs().get("E").getPrivate());
			} catch (Exception e) {
				throw new RuntimeException("opening envelope");
			}
			//Signed signedContent = Utils.parseSigned(str);
			//return signedContent.getUnsigned();
			return str;
		} else if (str.contains("MESSAGE")) {
			//str = Utils.unAsciiArmor(str, false);//set of additional operations
			throw new NotInEnvelopeException(str);
		} else
			throw new RuntimeException("unknown message type:\n" + str);
	}

	public Client(UserAccount account, String serverID, PublicKey serverPublicKey, Transport transport) {
		this(account, serverID, serverPublicKey, transport, null);
	}

	public Client(UserAccount account, String serverID, PublicKey serverPublicKey, Transport transport,
			String serverNymID) {
		super();
		this.userAccount = account;
		this.serverID = serverID;
		this.serverPublicKey = serverPublicKey;
		this.transport = transport;
		this.serverNymID = serverNymID == null ? Utils.toNymID(serverPublicKey) : serverNymID;
	}

	@Override
	public void close() throws IOException {
		transport.close();
	}

	public UserAccount getUserAccount() {
		return userAccount;
	}

	public String getServerID() {
		return serverID;
	}

	public String getServerNymID() {
		return serverNymID;
	}

	public PublicKey getServerPublicKey() {
		return serverPublicKey;
	}

	public Transport getTransport() {
		return transport;
	}

	public long getRequest() {
		if (reqNumManager != null)
			return reqNumManager.getReqNum(this);
		return getRequestRaw();
	}

	public long getRequestRaw() {
		MSG.GetRequest req = new MSG.GetRequest();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		MSG.Message msg = new MSG.Message();
		msg.setGetRequest(req);
		MSG.GetRequestResp resp = send(msg).getGetRequestResp();
		nullCheck(resp);
		if (!resp.getSuccess())
			throw new RuntimeException("no success response");
		return resp.getNewRequestNum();
	}

	public MSG.Message send(MSG.Message msg) {
		Engines.render(msg, getUserAccount().getCpairs().get("A").getPrivate());
		if (DEBUG_JSON)
			logger.debug("\n{\"status\": \"request\", \"message\":\n{}},", Engines.gson.toJson(msg));
		else if (DEBUG_RAW)
			logger.debug("\n{}", msg.getSigned());
		String signed = send_s(msg.getSigned());
		MSG.Message rmsg = new MSG.Message();
		rmsg.setSigned(signed);
		Engines.parse(rmsg);
		if (DEBUG_JSON)
			logger.debug("\n{\"status\": \"response\", \"message\":\n{}},", Engines.gson.toJson(rmsg));
		else if (DEBUG_RAW)
			logger.debug("\n{}", signed);
		return rmsg;
	}

	public MSG.CreateUserAccountResp createUserAccountNew(OT.User credentialList, OT.CredentialMap credentials) {
		MSG.CreateUserAccount req = new MSG.CreateUserAccount();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		//req.setRequestNum(getRequest());
		req.setRequestNum(1l);//TODO find out why
		req.setCredentialList(new MSG.AsciiEntity<OT.User>(credentialList));
		req.setCredentials(credentials);
		return send(new MSG.Message().set(req)).getCreateUserAccountResp();
	}

	public MSG.CheckUserResp checkUser(String nymID) {
		MSG.CheckUser req = new MSG.CheckUser();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setNymID2(nymID);
		req.setRequestNum(getRequest());
		MSG.Message msg = new MSG.Message();
		msg.setCheckUser(req);
		MSG.Message resp = send(msg);
		return resp.getCheckUserResp();
	}

	void nullCheck(MSG.Response resp) {
		if (resp == null)
			throw new RuntimeException("no response message");
	}

	public MSG.SendUserMessageResp sendUserMessage(String message, String recipientNymID,
			PublicKey recipientPublicKey) {
		MSG.SendUserMessage req = new MSG.SendUserMessage();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setNymID2(recipientNymID);
		req.setRequestNum(getRequest());
		try {
			ByteBuffer buff = Utils.seal(message, recipientNymID, recipientPublicKey);
			byte[] enc = new byte[buff.remaining()];
			buff.get(enc);
			req.setMessagePayload(new OT.ArmoredData(enc));
		} catch (Exception e) {
			throw new RuntimeException();
		}
		return send(new MSG.Message().set(req)).getSendUserMessageResp();
	}

	public MSG.GetNymboxResp getNymbox() {
		MSG.GetNymbox req = new MSG.GetNymbox();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setRequestNum(getRequest());
		MSG.Message msg = new MSG.Message();
		msg.setGetNymbox(req);
		MSG.Message resp = send(msg);
		return filter(resp.getGetNymboxResp());
	}

	public MSG.GetBoxReceiptResp getBoxReceipt(String accountID, OT.Ledger.Type boxType, long transactionNum) {
		MSG.GetBoxReceipt req = new MSG.GetBoxReceipt();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setRequestNum(getRequest());
		req.setAccountID(accountID);
		req.setBoxType(boxType);
		req.setTransactionNum(transactionNum);
		return send(new MSG.Message().set(req)).getGetBoxReceiptResp();
	}

	/*
	public Messages.GetOutbox getOutbox(String accountID) {
		String req = RequestTemplates.buildGetOutbox(account.getNymID(), serverID, getRequest(), accountID);
		String resp = send(req);
		return Messages.parseResponse(resp, Messages.GetOutbox.class);
	}
	*/

	public MSG.GetTransactionNumResp getTransactionNum(String nymboxHash) {
		MSG.GetTransactionNum req = new MSG.GetTransactionNum();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setRequestNum(getRequest());
		req.setNymboxHash(nymboxHash);
		return send(new MSG.Message().set(req)).getGetTransactionNumResp();
	}

	public MSG.CreateAccountResp createAccount(String assetType) {
		MSG.CreateAccount req = new MSG.CreateAccount();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setRequestNum(getRequest());
		req.setAssetType(assetType);
		return send(new MSG.Message().set(req)).getCreateAccountResp();
	}

	public MSG.GetInboxResp getInbox(String accountID) {
		MSG.GetInbox req = new MSG.GetInbox();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setRequestNum(getRequest());
		req.setAccountID(accountID);
		req = filter(req);
		return filter(send(new MSG.Message().set(req)).getGetInboxResp());
	}

	public MSG.GetOutboxResp getOutbox(String accountID) {
		MSG.GetOutbox req = new MSG.GetOutbox();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setRequestNum(getRequest());
		req.setAccountID(accountID);
		return send(new MSG.Message().set(req)).getGetOutboxResp();
	}

	public MSG.GetAccountResp getAccount(String accountID) {
		MSG.GetAccount req = new MSG.GetAccount();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setRequestNum(getRequest());
		req.setAccountID(accountID);
		return send(new MSG.Message().set(req)).getGetAccountResp();
	}

	public MSG.ProcessNymboxResp processNymbox(OT.Ledger ledger, String nymboxHash) {
		MSG.ProcessNymbox req = new MSG.ProcessNymbox();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setRequestNum(getRequest());
		req.setNymboxHash(nymboxHash);
		req.setProcessLedger(ledger);
		return send(new MSG.Message().set(req)).getProcessNymboxResp();
	}

	public MSG.ProcessInboxResp processInbox(OT.Ledger ledger, String nymboxHash) {
		MSG.ProcessInbox req = new MSG.ProcessInbox();
		req.setNymID(userAccount.getNymID());
		req.setServerID(serverID);
		req.setRequestNum(getRequest());
		req.setAccountID(ledger.getAccountID());
		req.setNymboxHash(nymboxHash);
		req.setProcessLedger(ledger);
		req = filter(req);
		return filter(send(new MSG.Message().set(req)).getProcessInboxResp());
	}

	public MSG.NotarizeTransactionsResp notarizeTransaction(OT.Ledger accountLedger, String nymboxHash) {
		MSG.NotarizeTransactions req = createNotarizeTransactionsReq(accountLedger, nymboxHash);
		return filter(send(new MSG.Message().set(filter(req))).getNotarizeTransactionsResp());
	}

	public MSG.NotarizeTransactions createNotarizeTransactionsReq(OT.Ledger accountLedger, String nymboxHash) {
		MSG.NotarizeTransactions req = new MSG.NotarizeTransactions();
		req.setServerID(serverID);
		req.setNymID(userAccount.getNymID());
		req.setAccountID(accountLedger.getAccountID());
		req.setNymboxHash(nymboxHash);
		req.setRequestNum(getRequest());
		req.setAccountLedger(accountLedger);
		return req;
	}

	public void setReqNumManager(ReqNumManager reqNumManager) {
		this.reqNumManager = reqNumManager;
	}

	@SuppressWarnings("serial")
	public static class NoResponseException extends RuntimeException {

	}

	@SuppressWarnings("serial")
	public static class NotInEnvelopeException extends RuntimeException {
		public NotInEnvelopeException(String message) {
			super("content:\n" + message);
		}
	}

	// filters

	public static int EVENT_STD = 1 << 0;

	public static interface Filter<T> {
		public T filter(T obj, Client client);

		public int getMask();
	}

	protected static class PrioritizedFilter {
		int priority;
		Class<?> clazz;
		Filter<?> filter;
	}

	protected List<PrioritizedFilter> filters = new ArrayList<PrioritizedFilter>();

	public <T> void addFilter(Filter<T> filter, Class<T> clazz, int priority) {
		if (filter == null)
			throw new IllegalArgumentException("filter == null");
		if (clazz == null)
			throw new IllegalArgumentException("clazz == null");
		PrioritizedFilter pf = new PrioritizedFilter();
		pf.filter = filter;
		pf.priority = priority;
		pf.clazz = clazz;
		for (int i = 0; i < filters.size(); i++)
			if (pf.priority < filters.get(i).priority) {
				filters.add(i, pf);
				return;
			}
		filters.add(pf);
	}

	protected <T> T filter(T object) {
		return filter(object, EVENT_STD);
	}

	@SuppressWarnings("unchecked")
	protected <T> T filter(T object, int event) {
		for (PrioritizedFilter f : filters)
			if ((event & f.filter.getMask()) > 0)
				if (f.clazz.isAssignableFrom(object.getClass()))
					object = ((Filter<T>) f.filter).filter(object, this);
		return object;
	}
}
