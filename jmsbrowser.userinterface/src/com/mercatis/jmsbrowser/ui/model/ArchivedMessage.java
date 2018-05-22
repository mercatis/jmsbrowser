package com.mercatis.jmsbrowser.ui.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import javax.jms.BytesMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.eclipse.core.runtime.Assert;

import com.mercatis.jms.JmsBrowserException;
import com.mercatis.jms.JmsConnection;
import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsMessageHelper;
import com.mercatis.jmsbrowser.ui.data.JmsProperty;
import com.mercatis.jmsbrowser.ui.data.MessageContainer.ColumnType;

public class ArchivedMessage implements Serializable {
	public static enum MessageType { TEXT, BYTE, OBJECT, STREAM };
	private static final long serialVersionUID = 4297135387616983456L;
	private final long guid;
	private String label;
	// JMS Properties
	private String correlationID = null;
	private Integer deliveryMode = null;
	private JmsDestination destination; // read only
	private Long expiration = null;
	private String messageID = null;
	private Integer priority = null;
	private Boolean redelivered = null;
	private JmsDestination replyTo = null;
	private Long timestamp = null;
	private String type = null;
	private Map<String, String> otherProperties = new TreeMap<String, String>();

	private MessageType payloadType = null;
	private byte payload[] = null;
	private Class<?> payloadClass = null;
	
	private boolean modified = false;

	public ArchivedMessage(MessageType t) {
		Random r = new Random(System.currentTimeMillis());
		guid = r.nextLong();
		payloadType = t;
		label = "New " + getTypeLabel(t);
		setTimestamp(System.currentTimeMillis());
		setDeliveryMode(2);
		setExpiration(0L);
		setPriority(4);
	}
	
	public String getPropertiesAsString() {
		StringBuilder sb = new StringBuilder();
		Set<Entry<String, String>> propSet = otherProperties.entrySet();
		for (Entry<String, String> e : propSet) {
			sb.append(e.getKey() + "=" + e.getValue() + ";");
		}
		sb.setLength(sb.length()-1);
		return sb.toString();
	}
	
	private void copyData(ArchivedMessage msg) {
		otherProperties.clear();
		label = msg.label;
		correlationID = msg.correlationID;
		deliveryMode = (msg.deliveryMode!=null)? new Integer(msg.deliveryMode) : null;
		destination = (msg.destination!=null)? msg.destination.clone() : null; // read only
		expiration = (msg.expiration!=null)? new Long(msg.expiration) : null;
		messageID = msg.messageID;
		priority = (msg.priority!=null)? new Integer(msg.priority) : null;
		redelivered = (msg.redelivered!=null)? new Boolean(msg.redelivered) : null;
		replyTo = (msg.replyTo!=null)? msg.replyTo.clone() : null;
		timestamp = (msg.timestamp!=null)? new Long(msg.timestamp) : null;
		type = msg.type;
		Iterator<String> it = msg.otherProperties.keySet().iterator();
		while (it.hasNext()) {
			String key = it.next();
			String value = msg.otherProperties.get(key);
			otherProperties.put(key, value);
		}
		payloadType = (msg.payloadType!=null)? msg.payloadType : null;
		if (msg.payload!=null) {
			payload = new byte[msg.payload.length];
			System.arraycopy(msg.payload, 0, payload, 0, msg.payload.length);
		} else {
			payload = null;
		}
		payloadClass = (msg.payloadClass!=null)? msg.payloadClass : null;
	}
	
	public ArchivedMessage(ArchivedMessage msg) {
		Random r = new Random(System.currentTimeMillis());
		guid = r.nextLong();
		copyData(msg);
	}
	
	public ArchivedMessage(Message msg, JmsConnection conn) {
		Random r = new Random(System.currentTimeMillis());
		guid = r.nextLong();
		// read data from message
		try { correlationID = msg.getJMSCorrelationID();
		} catch (Exception e) { }
		try { deliveryMode = Integer.valueOf(msg.getJMSDeliveryMode());
		} catch (Exception e) { }
		try { destination = conn.translateDestination(msg.getJMSDestination());
		} catch (Exception e) { }
		try { expiration = Long.valueOf(msg.getJMSExpiration());
		} catch (Exception e) { }
		try { messageID = msg.getJMSMessageID();
		} catch (Exception e) { }
		try { priority = Integer.valueOf(msg.getJMSPriority());
		} catch (Exception e) { }
		try { redelivered = Boolean.valueOf(msg.getJMSRedelivered());
		} catch (Exception e) { }
		try { replyTo = conn.translateDestination(msg.getJMSReplyTo());
		} catch (Exception e) { }
		try { timestamp = Long.valueOf(msg.getJMSTimestamp());
		} catch (Exception e) { }
		try { type = msg.getJMSType();
		} catch (Exception e) { }
		// read custom properties
		try {
			Enumeration<?> props = msg.getPropertyNames();
			while (props.hasMoreElements()) {
				String key = (String) props.nextElement();
				String value = msg.getStringProperty(key);
				otherProperties.put(key, value);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// create label
		if (messageID==null || messageID.length()==0)
			label = "<unnamed>";
		else
			label = new String(messageID);
		// get payload
		try {
			if (msg instanceof TextMessage) {
				payloadType = MessageType.TEXT;
				byte srcdata[] = ((TextMessage) msg).getText().getBytes();
				payload = new byte[srcdata.length];
				System.arraycopy(srcdata, 0, payload, 0, srcdata.length);
			} else if (msg instanceof BytesMessage) {
				payloadType = MessageType.BYTE;
				int l = (int) ((BytesMessage) msg).getBodyLength();
				payload = new byte[l];
				((BytesMessage) msg).readBytes(payload, l);
			} else if (msg instanceof ObjectMessage) {
				payloadType = MessageType.OBJECT;
				Object o = ((ObjectMessage) msg).getObject();
				payloadClass = o.getClass();
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(baos);
				oos.writeObject(o);
				oos.close();
				payload = baos.toByteArray();
			} else if (msg instanceof StreamMessage) {
				payloadType = MessageType.STREAM;
				// TODO: implement
			}
		} catch (Exception e) {
			System.err.println("payload parsing failed");
			e.printStackTrace();
		}
	}
	
	public Message createMessage(JmsConnection con) throws JmsBrowserException {
		final Message m;
		switch (payloadType) {
			case TEXT:
				if (payload!=null && payload.length>0)
					m = con.createTextMessage(new String(payload));
				else
					m = con.createTextMessage();
				break;
			case BYTE:
				m = con.createBytesMessage(payload);
				break;
//			case STREAM:
//				m = con.createStreamMessage();
//				break;
//			case OBJECT:
//				break;
			default:
				throw new JmsBrowserException("unsupported payload type "+payloadType.toString()+".");
		}
		try {  m.setJMSCorrelationID(correlationID);
		} catch (Exception e) { }
		try { m.setJMSDeliveryMode(deliveryMode);
		} catch (Exception e) { }
		try { m.setJMSDestination(con.createDestination(destination));
		} catch (Exception e) { }
		try { m.setJMSExpiration(expiration);
		} catch (Exception e) { }
		try { m.setJMSMessageID(messageID);
		} catch (Exception e) { }
		try { m.setJMSPriority(priority);
		} catch (Exception e) { }
		try { m.setJMSRedelivered(redelivered);
		} catch (Exception e) { }
		try { m.setJMSReplyTo(con.createDestination(replyTo));
		} catch (Exception e) { }
		try { m.setJMSTimestamp(timestamp);
		} catch (Exception e) { }
		try { m.setJMSType(type);
		} catch (Exception e) { }
		// write custom properties
		try {
			Iterator<String> it = otherProperties.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next();
				String value = otherProperties.get(key);
				try {
					m.setStringProperty(key, value);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return m;
	}
	
	public String getMessageType() {
		switch (payloadType) {
			case TEXT:
				return "TextMessage";
			case BYTE:
				return "ByteMessage";
			case OBJECT:
				return "ObjectMessage";
			case STREAM:
				return "StreamMessage";
			default:
				return "<unknown>";
		}
	}

	public void setLabel(String label) {
		this.label = label;
		modified = true;
	}

	public String getLabel() {
		return label;
	}
	
	public void saveToDisk(File path) throws IOException {
		boolean saveState = modified;
		modified = false;
		try {
			File file = new File(path, getFilename());
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(this);
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(baos.toByteArray());
			baos.close();
			fos.close();
		} catch(IOException e) {
			modified = saveState;
			throw e;
		}
	}

	public void revert(File file) throws IOException {
		boolean saveState = modified;
		modified = false;
		try {
			File f = new File(file, getFilename());
			if (f.exists()) {
				ArchivedMessage org = readFromDisk(f);
				copyData(org);
			} else
				throw new IOException("message was never saved");
		} catch(IOException e) {
			modified = saveState;
			throw e;
		}
	}
	
	public String getFilename() {
		return JmsMessageHelper.getHashHexString(guid);
	}

	public static ArchivedMessage readFromDisk(File f) throws IOException {
		Assert.isNotNull(f);
		Object o = null;
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
		try {
			o = ois.readObject();
		} catch (ClassNotFoundException e) {
			System.err.println(e.getMessage());
		}
		ois.close();
		if (o instanceof ArchivedMessage)
			return (ArchivedMessage) o;
		else
			throw new IOException("\"" + f.getName() + "\" contains no ArchivedMessage.");
	}

	public void setCorrelationID(String jmsCorrelationID) {
		correlationID = jmsCorrelationID;
		modified = true;
	}

	public String getCorrelationID() {
		return correlationID;
	}

	public void setDeliveryMode(int jmsDeliveryMode) {
		deliveryMode = jmsDeliveryMode;
		modified = true;
	}

	public Integer getDeliveryMode() {
		return deliveryMode;
	}

	public void setReplyTo(JmsDestination jmsReplyTo) {
		replyTo = jmsReplyTo;
		modified = true;
	}

	public JmsDestination getReplyTo() {
		return replyTo;
	}

	public void setExpiration(Long jmsExpiration) {
		expiration = jmsExpiration;
		modified = true;
	}

	public Long getExpiration() {
		return expiration;
	}

	public void setMessageID(String jmsMessageID) {
		messageID = jmsMessageID;
		modified = true;
	}

	public String getMessageID() {
		return messageID;
	}

	public void setPriority(Integer jmsPriority) {
		priority = jmsPriority;
		modified = true;
	}

	public Integer getPriority() {
		return priority;
	}

	public void setRedelivered(Boolean jmsRedelivered) {
		redelivered = jmsRedelivered;
		modified = true;
	}

	public Boolean getRedelivered() {
		return redelivered;
	}

	public void setTimestamp(Long jmsTimestamp) {
		timestamp = jmsTimestamp;
		modified = true;
	}

	public Long getTimestamp() {
		return timestamp;
	}

	public void setType(String jmsType) {
		type = jmsType;
		modified = true;
	}

	public String getType() {
		return type;
	}
	
	public void setJmsProperty(JmsProperty jp) throws NumberFormatException {
		if (!jp.editable)
			return;
		if (jp.deleteable) {
			setProperty(jp.name, jp.value);
			return;
		}
		String key = jp.name;
		String value = jp.value;
		
		if ("Label".equals(key)) {
			setLabel(value);
		} else if ("Correlation ID".equals(key)) {
			setCorrelationID(value);
		} else if ("Delivery Mode".equals(key)) {
			setDeliveryMode(Integer.parseInt(value));
		} else if ("Expiration".equals(key)) {
			setExpiration(Long.parseLong(value));
		} else if ("Priority".equals(key)) {
			setPriority(Integer.parseInt(value));
		} else if ("Type".equals(key)) {
			setType(value);
		} else
			throw new RuntimeException("internal error 1709");
		modified = true;
	}
	
	public void setProperty(String key, String value) {
		Assert.isNotNull(key);
		if (!otherProperties.containsKey(key) && value==null)
			return;
		if (value==null)
			otherProperties.remove(key);
		else
			otherProperties.put(key, value);
		modified = true;
	}
	
	public String getProperty(String key) {
		Assert.isNotNull(key);
		if (otherProperties.containsKey(key))
			return otherProperties.get(key);
		return null;
	}
	
	public void removeProperty(String key) {
		Assert.isNotNull(key);
		if (otherProperties.containsKey(key))
			otherProperties.remove(key);
		modified = true;
	}
	
	public final Set<String> getPropertyNames() {
		return otherProperties.keySet();
	}

	public JmsDestination getDestination() {
		return destination;
	}

	public byte[] getPayload() {
		return payload;
	}
	
	public void setPayload(InputStream stream) throws IOException {
		int size = stream.available();
		payload = new byte[size];
		stream.read(payload);
		modified = true;
	}

	public void setPayload(byte data[]) {
		setPayload(data, data.length);
	}
	
	public void setPayload(byte data[], int noBytes) {
		if (data!=null) {
			payload = new byte[noBytes];
			System.arraycopy(data, 0, payload, 0, noBytes);
		} else {
			payload = null;
		}
		modified = true;
	}
	
	public int getPayloadSize() {
		if (payload==null)
			return 0;
		else
			return payload.length; 
	}
	
	public boolean isTextMessage() {
		return payloadType==MessageType.TEXT;
	}

	public boolean isBytesMessage() {
		return payloadType==MessageType.BYTE;
	}

	public boolean isStreamMessage() {
		return payloadType==MessageType.STREAM;
	}

	public boolean isObjectMessage() {
		return payloadType==MessageType.OBJECT;
	}

	public boolean deleteFromDisk(File path) throws IOException {
		String filename = getFilename();
		File f = new File(path, filename);
//		FileWriter out = new FileWriter(f);
//		out.close();
		boolean success = f.delete();
		if (!success)
//			System.err.println("Could not delete file " + filename);
			throw new IOException("error deleting file.");
		return success;
	}

	public Class<?> getPayloadClass() {
		return payloadClass;
	}

	public boolean isModified() {
		return modified;
	}
	
	public void setModified(boolean modified) {
		this.modified = modified;
	}
	
	public static String getTypeLabel(MessageType t) {
		switch(t) {
			case TEXT:
				return "TextMessage";
			case BYTE:
				return "ByteMessage";
			case STREAM:
				return "StreamMessage";
			case OBJECT:
				return "ObjectMessage";
		}
		return null;
	}
	
	public static final Comparator<ArchivedMessage> getComperator(ColumnType col, boolean up) {
		final int mult = up ? 1 : -1;
		switch (col) {
			case LABEL:
				return new Comparator<ArchivedMessage>() {
					@Override
					public int compare(ArchivedMessage o1, ArchivedMessage o2) {
						return mult * o1.getLabel().compareTo(o2.getLabel());
					}
				};
			case TSTAMP:
					return new Comparator<ArchivedMessage>() {
						@Override
						public int compare(ArchivedMessage o1, ArchivedMessage o2) {
							return mult * o1.getTimestamp().compareTo(o2.getTimestamp());
						}
					};
			default:
				throw new RuntimeException("unsupported column type");
		}
	}
}
