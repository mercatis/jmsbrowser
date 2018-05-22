package com.mercatis.jmsbrowser.ui.data;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jms.JmsDestinationListener;
import com.mercatis.jms.JmsMessageHelper;
import com.mercatis.jms.log.LogUtil;
import com.mercatis.jmsbrowser.ui.listener.MessageReveiverChangedListener;
import com.mercatis.jmsbrowser.ui.util.FormatUtil;
import com.mercatis.jmsbrowser.ui.util.store.IconStore;

public class MessageReceiver implements JmsDestinationListener, Listener {
	public static enum ColumnType { TSTAMP, MSGID, PROPS, PAYLOAD };
	private int maxsize = 500;
	private boolean waitForQueueStart = false;

	private List<Message> messages = new LinkedList<Message>();
	private List<Message> sortedData = new LinkedList<Message>();
	
	private JmsConnection connection;
	private JmsDestination destination;
	private boolean listen = true;
	
	private ReadWriteLock mutex = new ReentrantReadWriteLock();
	
	private int nextIdx = 0;
	private Comparator<Message> sortFunc;
	
	private String filterString;
	private boolean threadRunning = true;
	
	private MessageFilterUpdater updater;
	private long keyEvent;
	private String newFilter;
	private Boolean filters[] = new Boolean[ColumnType.values().length];
	private MessageReveiverChangedListener listener;
	
	private class MessageFilterUpdater extends Thread {
		@Override
		public void run() {
			try {
				while(threadRunning) {
					long l = keyEvent-System.currentTimeMillis();
					if (l>0)
						synchronized(updater) { updater.wait(l); }
					else {
						mutex.readLock().lock();
						filterString = newFilter;
						sortedData.clear();
						if (filterString!=null) {
							for(Message m : messages)
								if (filterMatch(m))
									sortedData.add(m);
							Collections.sort(sortedData, sortFunc);
						} else {
							sortedData.addAll(messages);
						}
						listener.onMessageChangedAll(messages.size(), sortedData.size());
						mutex.readLock().unlock();
						synchronized(updater) { updater.wait(); }
					}
				}
			} catch (InterruptedException e) {}
		}
	}
	
	public MessageReceiver(JmsConnection conn, JmsDestination dest, int maxMessages, MessageReveiverChangedListener listener) throws JmsBrowserException {
		this.listener = listener;
		maxsize = maxMessages;
		connection = conn;
		destination = dest;
		connection.addMessageListener(destination, this);
		for (ColumnType ct : ColumnType.values())
			filters[ct.ordinal()] = true;
		sortFunc = JmsMessageHelper.compTimestampAsc;
	}
	
	
	@Override
	public void onMessage(Message msg) {
		if(!listen)
			return;
		try {
			if (destination.type!=DestinationType.QUEUE) {
				if (messages.size() >= maxsize)
					removeMessage(0);
				appendMessage(msg);
			} else {
				// if msg is null, we get notified about the end of an update cycle
				if (msg == null) {
					if (nextIdx==0) {
						removeAll();
					} else {
						removeMessage(nextIdx, messages.size()-1);
						nextIdx=0;
					}
					waitForQueueStart = false;
				} else {
					if (waitForQueueStart)
						return;
					long currentTimestamp = msg.getJMSTimestamp();
					int totalMsgs=messages.size();
					while(nextIdx<totalMsgs) {
						Message exMsg = messages.get(nextIdx);
						long exTimestamp = exMsg.getJMSTimestamp();
						if (exTimestamp==currentTimestamp && JmsMessageHelper.compareMessage(msg, exMsg)) {
							++nextIdx;
							return; // message exists, nothing left to do
						}
						if (exTimestamp>currentTimestamp) {
							totalMsgs = insertMessage(msg, nextIdx);
							++nextIdx;
							return; // message inserted, nothing left to do
						}
						if (exTimestamp<=currentTimestamp) {
							totalMsgs = removeMessage(nextIdx);
							continue;
						}
					}
					appendMessage(msg);
					++nextIdx;
				}
			}
		} catch (JMSException e) {
			e.printStackTrace();
			removeAll();
		}
	}

	@Override
	public void onPurge() {
		LogUtil.logInfo(destination.toString()+" got purged");
		removeAll();
	}

	private int removeMessage(int from, int to) {
		mutex.writeLock().lock();
		int size = messages.size();
		for (int i=to; i>=from; --i) {
			Message delMsg = messages.remove(i);
			if (filterString!=null) {
				if (filterMatch(delMsg)) {
					int idx = sortedData.indexOf(delMsg);
					sortedData.remove(idx);
					listener.onMessageRemoved(idx, messages.size(), sortedData.size());
				}
			} else {
				sortedData.remove(i);
				listener.onMessageRemoved(i, messages.size(), sortedData.size());
			}
		}
		mutex.writeLock().unlock();
		return size;
	}
	
	public void removeMessage(Message msg) {
		mutex.writeLock().lock();
		int idx = messages.indexOf(msg);
		if (idx>=0) {
			messages.remove(idx);
			int size = messages.size();
			if (filterString!=null) {
				if (filterMatch(msg)) {
					idx = sortedData.indexOf(msg);
					sortedData.remove(idx);
					listener.onMessageRemoved(idx, size, sortedData.size());
				}
			} else {
				sortedData.remove(idx);
				listener.onMessageRemoved(idx, size, sortedData.size());
			}
		}
		mutex.writeLock().unlock();
	}

	private int removeMessage(int index) {
		mutex.writeLock().lock();
		final Message m = messages.remove(index);
		int size = messages.size();
		if (filterString!=null) {
			if (filterMatch(m)) {
				int idx = sortedData.indexOf(m);
				sortedData.remove(idx);
				listener.onMessageRemoved(idx, size, sortedData.size());
			}
		} else {
			sortedData.remove(index);
			listener.onMessageRemoved(index, size, sortedData.size());
		}
		mutex.writeLock().unlock();
		return size;
	}
	
	private void appendMessage(Message msg) {
		mutex.writeLock().lock();
		messages.add(msg);
		if (filterString!=null) {
			if (filterMatch(msg)) {
				sortedData.add(0, msg);
				Collections.sort(sortedData, sortFunc);
				listener.onNewMessage(sortedData.indexOf(msg), msg, messages.size(), sortedData.size());
			}
		} else {
			sortedData.add(msg);
			listener.onNewMessage(-1, msg, messages.size(), sortedData.size());
		}
		mutex.writeLock().unlock();
	}
	
	private boolean filterMatch(Message msg) {
		if (filterString==null)
			return true;
		try {
			if (filters[ColumnType.TSTAMP.ordinal()] && FormatUtil.DATE_FORMAT.format(new Date(msg.getJMSTimestamp())).contains(filterString))
				return true;
			if (filters[ColumnType.PROPS.ordinal()] && JmsMessageHelper.getPropertiesString(msg).contains(filterString))
				return true;
			if (filters[ColumnType.PAYLOAD.ordinal()] && msg instanceof TextMessage) {
				String s = ((TextMessage) msg).getText();
				if (s!=null && s.contains(filterString)) {
					return true;
				}
			}
		} catch (JMSException e) {
			System.err.println("Error matching message: "+e.getMessage());
		}
		return false;
	}

	private int insertMessage(Message msg, final int index) {
		mutex.writeLock().lock();
		messages.add(index, msg);
		if (filterString!=null) {
			if (filterMatch(msg)) {
				sortedData.add(0, msg);
				Collections.sort(sortedData, sortFunc);
				listener.onNewMessage(sortedData.indexOf(msg), msg, messages.size(), sortedData.size());
			}
		} else {
			sortedData.add(index, msg);
			listener.onNewMessage(index, msg, messages.size(), sortedData.size());
		}				
		int size = messages.size();
		mutex.writeLock().unlock();
		return size;
	}
	
	private void removeAll() {
		mutex.writeLock().lock();
		int size=messages.size();
		if (size!=0) {
			messages.clear();
			sortedData.clear();
		}
		listener.onMessageRemovedAll();
		waitForQueueStart = true;
		mutex.writeLock().unlock();
	}
	
	public void toggleListener(boolean enable) {
		if((listen && enable) || !(listen || enable))
			return;
		if (enable)
			waitForQueueStart = true;
		listen = enable;
	}
	
	public void dispose() {
		try {
			toggleListener(false);
			connection.removeMessageListener(destination, this);
		} catch (JmsBrowserException e) {
			e.printStackTrace();
		}
	}

	public void removeAllMessages() {
		removeAll();
	}
	
	private String getFormattedBody(TextMessage tm) throws JMSException {
		String body = tm.getText();
		if (body.startsWith("<?xml")) {
			// we assume its xml and we delete the first element
			int endOfFirst = body.indexOf('>', 1)+1;
			return body.substring(endOfFirst);
		}
		return body;
	}
	
	public void changeMaxMessages(int newMax) {
		maxsize = newMax;
		if (destination.type==DestinationType.QUEUE) {
			removeAll();
		}
	}
	
	public void setFilterString(String filter) {
		keyEvent = System.currentTimeMillis()+250;
		newFilter = filter.length()==0 ? null : filter;
		if (updater==null) {
			updater=new MessageFilterUpdater();
			updater.setDaemon(true);
			updater.start();
		} else {
			synchronized (updater) {
				updater.notify();
			}
		}
	}
	
	public void setFilterTarget(ColumnType col, boolean enable) {
		filters[col.ordinal()] = enable;
		if (updater==null) {
			updater=new MessageFilterUpdater();
			updater.setDaemon(true);
			updater.start();
		} else {
			synchronized (updater) {
				updater.notify();
			}
		}
	}
	
	public void setSortColumn(ColumnType ct, int direction) {
		boolean up = direction == SWT.UP; 
		switch (ct) {
			case TSTAMP:
				sortFunc = up ? JmsMessageHelper.compTimestampDesc : JmsMessageHelper.compTimestampAsc;
				break;
			case MSGID:
				sortFunc = up ? JmsMessageHelper.compMsgIdDesc : JmsMessageHelper.compMsgIdAsc;
				break;
			case PROPS:
				sortFunc = up ? JmsMessageHelper.compPropsDesc : JmsMessageHelper.compPropsAsc;
				break;
			case PAYLOAD:
				sortFunc = up ? JmsMessageHelper.compTimestampDesc : JmsMessageHelper.compTimestampAsc;
				break;
		}
		Collections.sort(sortedData, sortFunc);
		listener.onMessageChangedAll(messages.size(), sortedData.size());
	}
	
	private String getColumnText(Message msg, ColumnType ct) {
		try {
			switch (ct) {
				case TSTAMP:
					return FormatUtil.DATE_FORMAT.format(new Date(msg.getJMSTimestamp()));
				case MSGID:
					return msg.getJMSMessageID();
				case PROPS:
					return JmsMessageHelper.getPropertiesString(msg);
				case PAYLOAD:
					if (msg instanceof TextMessage)
						try {
							return getFormattedBody((TextMessage) msg);
						} catch (JMSException e) {
							return "<error reading payload>";
						}
						return "<no text payload>";
				default:
					return "<internal error>";
			}
		} catch (JMSException e) {
			LogUtil.logError("Error creating message label: "+e.getMessage(), e);
			return "<error>";
		}
	}

	// Listener (for SWT.SetData)
	@Override
	public void handleEvent(Event event) {
		TableItem item = (TableItem) event.item;
		int index = event.index;
		Message msg = sortedData.get(index);
		item.setData(msg);
		TableColumn columns[] = item.getParent().getColumns();
		String labels[] = new String[columns.length];
		for (int i=0; i<columns.length; ++i)
			labels[i] = getColumnText(msg, (ColumnType) columns[i].getData());
		item.setText(labels);
		item.setImage(IconStore.getMessageIcon(msg));
	}


	public boolean isQueue() {
		return destination.type == DestinationType.QUEUE;
	}
	
	public JmsDestination getDestination() {
		return destination;
	}
}
