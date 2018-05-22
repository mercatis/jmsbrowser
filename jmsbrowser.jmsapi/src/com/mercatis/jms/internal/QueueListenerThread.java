package com.mercatis.jms.internal;

import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import javax.jms.Message;
import javax.jms.QueueBrowser;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsDestinationListener;

public class QueueListenerThread extends Thread {
	private List<JmsDestinationListener> listeners;
	private Boolean threadRunning = true;
	private boolean sleep = false;
	private JmsConnection connection;
	private String queueName;
	private final int sleepDelay;
	
	public QueueListenerThread(String n, JmsConnection conn, int refreshDelay) {
		setDaemon(true);
		setName(n + "-listener");
		sleepDelay = refreshDelay;
		connection = conn;
		queueName = n;
		listeners = new LinkedList<JmsDestinationListener>();
		start();
	}
	
	public void addListener(JmsDestinationListener listener) {
		synchronized(listeners) {
			int idx = listeners.indexOf(listener);
			if (idx>=0)
				listeners.remove(idx);
			listeners.add(listener);
		}
	}
	
	public void removeListener(JmsDestinationListener listener) {
		synchronized(listeners) {
			int idx = listeners.indexOf(listener);
			if (idx>=0)
				listeners.remove(idx);
		}
	}
	
	public void shutdown() {
		synchronized (threadRunning) {
			threadRunning = false;
		}
		try {
			this.notify();
			this.join();
		} catch (InterruptedException e) {
		}
		listeners.clear();
	}
	
	private void notifyListeners(Message msg) {
		synchronized(listeners) {
			for (JmsDestinationListener listener : listeners) {
				try {
					// listener errors should not kill the thread
					listener.onMessage(msg);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	public void run() {
		Message msg;
		boolean loop = true;
		QueueBrowser qb;
		while (loop) {
			try {
				if (!listeners.isEmpty()) {
					qb = connection.createQueueBrowser(queueName);
					Enumeration<?> msgEnum = qb.getEnumeration();
					while (msgEnum.hasMoreElements()) {
						msg = (Message) msgEnum.nextElement();
						notifyListeners(msg);
					}
					qb.close();
					notifyListeners(null);
				}
				Thread.sleep(sleepDelay);
				synchronized(this) {
					if (sleep)
						wait();
				}
				synchronized (threadRunning) {
					loop = threadRunning;
				}
			} catch (Exception ex) {
				try {
					System.out.println("[" + getId() + "] error in queue listener thread - going to sleep (" + ex.getMessage() + ")");
					synchronized(this) {
						wait();
					}
					System.out.println("[" + getId() + "] yawn! - back at work");
				} catch (InterruptedException e) {
					// exit gracefully
					loop = false;
				}
			}
		}
	}
	
	public void enterSandman() {
		synchronized(this) {
			sleep = true;
		}
	}

	public void wakeUp() {
		synchronized (this) {
			notify();
		}
	}
}
