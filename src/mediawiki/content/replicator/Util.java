/*
 * Copyright 2015 Kulikov Dmitriy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mediawiki.content.replicator;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;

public class Util
{
	public static final String USER_AGENT = "MediaWiki Content Replicator";
	
	public static final int BUFFER_SIZE = 1 * 1024 * 1024;
	
	public static int pagesPerRequest = 50;
	public static long projectSaveInterval = 5 * 60 * 1000;
	
	public static final char[] RESTRICTED_CHARS = {'\\', '/', ':', '*', '?', '\"', '<', '>', '|'}; // "\\/:*?\"<>|";
	public static final char[] VALIDATED_CHARS =  {' ',  ' ', ' ', '^', '~', '\'', '(', ')', '-'}; // "   ^~'()-";
	
	public static String validateFileName(String filename)
	{
		for(int i = 0; i < RESTRICTED_CHARS.length; i++)
		{
			filename = filename.replace(RESTRICTED_CHARS[i], VALIDATED_CHARS[i]);
		}
		
		if(filename.startsWith("."))
		{
			filename = "!" + filename;
		}

		return filename;
	}
	
	public static void moveFile(File source, File dest) throws IOException
	{
		if(source.isDirectory())
		{
			dest.mkdirs();
			
			for(String file : source.list())
			{
				moveFile(new File(source, file), new File(dest, file));
			}
			
			source.delete();
		}
		else
		{
			source.renameTo(dest);
		}
	}
	
	public static void deleteFile(File source) throws IOException
	{
		if(source.isDirectory())
		{
			for(File file : source.listFiles())
			{
				deleteFile(file);
			}
		}
		
		source.delete();
	}
	
	public static String noEmpty(String text, String placeholder)
	{
		if(text == null || text.isEmpty())
		{
			return placeholder;
		}
		else
		{
			return text;
		}
	}
	
	public static String substring(String text, String start, String end)
	{
		int startindex = text.indexOf(start);
		
		if(startindex >= 0)
		{
			startindex += start.length();
			int endindex = text.indexOf(end, startindex);
			
			if(endindex >= 0)
			{
				return text.substring(startindex, endindex);
			}
		}
		
		return null;
	}
	
	/**
	 * Преобразовать число в строку вида 12 345 678
	 */
	public static String formatNumber(long value)
	{
		if(value < 0)
		{
			return "-" + formatNumber(-value);
		}

		StringBuilder res = new StringBuilder();
		String str;

		while(true)
		{
			if(value > 999)
			{
				res.insert(0, str = Long.toString(value % 1000));

				if(str.length() == 1)
				{
					res.insert(0, " 00");
				}
				else if(str.length() == 2)
				{
					res.insert(0, " 0");
				}
				else
				{
					res.insert(0, " ");
				}

				value /= 1000;
			}
			else
			{
				res.insert(0, Long.toString(value));
				break;
			}
		}

		return res.toString();
	}
	
	public static String encodeURL(String text)
	{
		try
		{
			return URLEncoder.encode(text, "UTF-8");
		}
		catch(UnsupportedEncodingException ex)
		{
			return text;
		}
	}
	
	public static void writeUTF(DataOutput dos, String text) throws IOException
	{
		if(text != null)
		{
			dos.writeBoolean(true);
			dos.writeUTF(text);
		}
		else
		{
			dos.writeBoolean(false);
		}
	}
	
	public static String readUTF(DataInput dis) throws IOException
	{
		if(dis.readBoolean())
		{
			return dis.readUTF();
		}
		else
		{
			return null;
		}
	}
	
	public static InputStream openConnection(String url) throws IOException
	{
		HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection();

		connection.setRequestMethod("GET");
		connection.setInstanceFollowRedirects(true);

		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setRequestProperty("Accept-Encoding", "gzip");

		InputStream is = connection.getInputStream();

		if("gzip".equalsIgnoreCase(connection.getContentEncoding()))
		{
			is = new GZIPInputStream(is);
		}
		
		return is;
	}
	
	public static byte[] downloadFile(String url, ProgressMonitor progress) throws IOException
	{
		HttpURLConnection connection = (HttpURLConnection)(new URL(url)).openConnection();
		
		connection.setRequestMethod("GET");
		connection.setInstanceFollowRedirects(true);
		
		int total = connection.getContentLength();
		
		if(total >= 0)
		{
			progress.setOperationProgress(0);
			progress.setOperationLimit(total);
		}
		else
		{
			progress.setOperationProgress(0);
			progress.setOperationLimit(1);
		}
		
		InputStream in = connection.getInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		byte[] buf = new byte[0x10000];
		int len;
		
		while(true)
		{
			len = in.read(buf);
			
			if(len > 0)
			{
				out.write(buf, 0, len);
				progress.progressOperation(len);
			}
			else if(len < 0)
			{
				break;
			}
		}
		
		in.close();
		
		buf = out.toByteArray();
		out.close();
		
		return buf;
	}
	
	private static MessageDigest MD5;
	
	public static String md5(String text)
	{
		if(text == null)
		{
			return null;
		}
		
		if(MD5 == null)
		{
			try
			{
				MD5 = MessageDigest.getInstance("MD5");
			}
			catch(NoSuchAlgorithmException ex)
			{
				return null;
			}
		}
		
		byte[] data;
		
		try
		{
			data = text.getBytes("UTF-8");
		}
		catch(UnsupportedEncodingException ex)
		{
			return null;
		}
		
		MD5.reset();
		data = MD5.digest(data);
		
		StringBuilder res = new StringBuilder(data.length * 2);
		
		for(int i = 0; i < data.length; i++)
		{
			int b = (int)data[i] & 0xFF;
			
			if(b < 0x10)
			{
				res.append('0').append(Integer.toHexString(b));
			}
			else
			{
				res.append(Integer.toHexString(b));
			}
		}
		
		return res.toString();
	}
	
	public static String timestampToURL(String timestamp)
	{
		return timestamp.replace("-", "").replace(":", "").replace("T", "").replace("Z", "");
	}
}
