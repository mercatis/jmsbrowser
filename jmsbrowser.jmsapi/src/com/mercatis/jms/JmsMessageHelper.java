package com.mercatis.jms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Enumeration;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public final class JmsMessageHelper {
	private static MessageDigest digest = null;
	
	private static MessageDigest getDigest() {
		if (digest==null) {
			try {
				digest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
		}
		return digest;
	}

	public static boolean compareMessage(Message arg0, Message arg1) {
		try {
			return arg0.getJMSTimestamp()==arg1.getJMSTimestamp() && arg0.getJMSMessageID().equals(arg1.getJMSMessageID());
		} catch (JMSException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static boolean compareMessageEnumerations(Enumeration<Message> enum0, Enumeration<Message> enum1) {
		boolean result = true;
		while (result) {
			if (!enum0.hasMoreElements() && !enum1.hasMoreElements())
				break;
			result &= enum0.hasMoreElements() && enum1.hasMoreElements();
			if (result)
				result &= compareMessage(enum0.nextElement(), enum1.nextElement());
		}
		return result;
	}
	
	public static String prettyFormatXml(String input, int indent) {
		String origStart = input.substring(0, 12);
		try {
			Source xmlInput = new StreamSource(new StringReader(input));
			StringWriter stringWriter = new StringWriter();
			StreamResult xmlOutput = new StreamResult(stringWriter);
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));
			transformer.transform(xmlInput, xmlOutput);
			String result = xmlOutput.getWriter().toString();
			if (!result.startsWith(origStart)) {
				int i = result.indexOf(origStart);
				return result.substring(i);
			}
			return result;
		} catch (Exception e) {
			return input;
		}
	}

	public static String prettyFormatXml(String input) {
	    return prettyFormatXml(input, 2);
	}

	
	private static int getObjectSize(Object o) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream() ;
			ObjectOutputStream out = new ObjectOutputStream(bos) ;
			out.writeObject(o);
			out.close(); // Get the bytes of the serialized object
			return bos.toByteArray().length;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return -1;
	}
	
	public static int getPayloadSize(Message msg) {
		try {
			if (msg instanceof TextMessage)
				return ((TextMessage) msg).getText().length();
			if (msg instanceof BytesMessage)
				return (int) ((BytesMessage) msg).getBodyLength();
			if (msg instanceof ObjectMessage) {
				return getObjectSize(((ObjectMessage) msg).getObject());
			}
		} catch (JMSException e) {
			e.printStackTrace();
		} catch (RuntimeException re) {
			// do nothing, normally this happens if getObject cannot find the object type
		}
		return -1;
	}
	
	public static String getHashHexString(long l) {
		MessageDigest digest = getDigest();
		final int length = 8;
		byte b[] = new byte[length];  
		for(int i= 0; i < length; i++){  
			b[length - i - 1] = (byte)(l >>> (i * 8));  
		}  
		StringBuffer hexString = new StringBuffer();
		byte messageDigest[] = digest.digest(b);
		for (int i=0;i<messageDigest.length;i++) {
			hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
		}
		return hexString.toString().toUpperCase();
	}
	
	public static String getPropertiesString(Message msg) throws JMSException {
		StringBuilder sb = new StringBuilder();
		Enumeration<?> propEnum = msg.getPropertyNames();
		while(propEnum.hasMoreElements()) {
			String name = (String) propEnum.nextElement();
			sb.append(name + "=" + msg.getStringProperty(name));
			if (propEnum.hasMoreElements())
				sb.append("; ");
		}
		return sb.toString();
	}
	
	public static final Comparator<Message> compTimestampAsc = new Comparator<Message>() {
		@Override
		public int compare(Message arg0, Message arg1) {
			try {
				long val0 = arg0.getJMSTimestamp();
				long val1 = arg1.getJMSTimestamp(); 
				if (val0 == val1) return 0;
				return val0 < val1 ? 1 : -1;
			} catch (JMSException e) {
				e.printStackTrace();
				return 0; 
			}
		}
	}; 

	public static final Comparator<Message> compMsgIdAsc = new Comparator<Message>() {
		@Override
		public int compare(Message arg0, Message arg1) {
			try {
				String val0 = arg0.getJMSMessageID();
				String val1 = arg1.getJMSMessageID(); 
				return val0.compareToIgnoreCase(val1);
			} catch (JMSException e) {
				e.printStackTrace();
				return 0;
			}
		}
	}; 

	public static final Comparator<Message> compPropsAsc = new Comparator<Message>() {
		@Override
		public int compare(Message arg0, Message arg1) {
			try {
				String val0 = JmsMessageHelper.getPropertiesString(arg0);
				String val1 = JmsMessageHelper.getPropertiesString(arg1);
				return val0.compareToIgnoreCase(val1);
			} catch (JMSException e) {
				e.printStackTrace();
				return 0;
			}
		}
	}; 

	public static final Comparator<Message> compTimestampDesc = new Comparator<Message>() {
		@Override
		public int compare(Message arg0, Message arg1) {
			try {
				long val0 = arg0.getJMSTimestamp();
				long val1 = arg1.getJMSTimestamp(); 
				if (val0 == val1) return 0;
				return val0 < val1 ? -1 : 1;
			} catch (JMSException e) {
				e.printStackTrace();
				return 0; 
			}
		}
	}; 

	public static final Comparator<Message> compMsgIdDesc = new Comparator<Message>() {
		@Override
		public int compare(Message arg0, Message arg1) {
			try {
				String val0 = arg0.getJMSMessageID();
				String val1 = arg1.getJMSMessageID(); 
				return -1 * val0.compareToIgnoreCase(val1);
			} catch (JMSException e) {
				e.printStackTrace();
				return 0;
			}
		}
	}; 

	public static final Comparator<Message> compPropsDesc = new Comparator<Message>() {
		@Override
		public int compare(Message arg0, Message arg1) {
			try {
				String val0 = JmsMessageHelper.getPropertiesString(arg0);
				String val1 = JmsMessageHelper.getPropertiesString(arg1);
				return -1 * val0.compareToIgnoreCase(val1);
			} catch (JMSException e) {
				e.printStackTrace();
				return 0;
			}
		}
	}; 
}
