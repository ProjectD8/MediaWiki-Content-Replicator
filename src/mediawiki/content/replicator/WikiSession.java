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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.TreeMap;

public class WikiSession
{
	protected MultiPartPost mpp;
	protected String apiurl;
	
	protected String defaultUser;
	protected String defaultPassword;
	
	protected String currentUser;
	protected String currentPassword;
	
	protected String edittoken;
	protected String importtoken;
	
	public WikiSession(String url, String defaultUser, String defaultPassword) throws IOException
	{
		mpp = new MultiPartPost();
		apiurl = url;
		
		this.defaultUser = defaultUser;
		this.defaultPassword = defaultPassword;
	}
	
	public void login(WikiUser user) throws IOException
	{
		if(user != null)
		{
			login(user.getName(), user.getPassword());
		}
		else
		{
			login(defaultUser, defaultPassword);
		}
	}
	
	public void login(String user, String password) throws IOException
	{
		if(user.equals(currentUser))
		{
			return;
		}
		
		if(currentUser != null)
		{
			logout();
		}
		
		mpp.addParam("format", "xml");
		mpp.addParam("action", "login");
		mpp.addParam("lgname", user);
		mpp.addParam("lgpassword", password);
		
		String response = mpp.post(apiurl);
		String result = Util.substring(response, "result=\"", "\"");
		
		if(result.equalsIgnoreCase("NeedToken"))
		{
			String token = Util.substring(response, "token=\"", "\"");
			
			mpp.addParam("format", "xml");
			mpp.addParam("action", "login");
			mpp.addParam("lgname", user);
			mpp.addParam("lgpassword", password);
			mpp.addParam("lgtoken", token);
			
			response = mpp.post(apiurl);
			result = Util.substring(response, "result=\"", "\"");
		}
		
		if(!result.equalsIgnoreCase("Success"))
		{
			throw new IOException(result);
		}
		
		mpp.addParam("format", "xml");
		mpp.addParam("action", "tokens");
		mpp.addParam("type", "edit|import");
		
		response = mpp.post(apiurl);
		
		edittoken = Util.substring(response, "edittoken=\"", "\"");
		importtoken = Util.substring(response, "importtoken=\"", "\"");
		
		currentUser = user;
		currentPassword = password;
	}
	
	public void logout() throws IOException
	{
		currentUser = null;
		currentPassword = null;
		
		mpp.addParam("format", "xml");
		mpp.addParam("action", "logout");
		
		mpp.post(apiurl);
	}
	
//	public int importPage(WikiPage p) throws IOException
//	{
//		login(defaultUser, defaultPassword);
//		
//		mpp.addParam("format", "xml");
//		mpp.addParam("action", "import");
//		mpp.addElement("xml", "dump.xml", false, p);
//		mpp.addParam("token", importtoken);
//		
//		String response = mpp.post(apiurl);
//		String result = Util.substring(response, "revisions=\"", "\"");
//		
//		if(result != null)
//		{
//			return Integer.parseInt(result);
//		}
//		else
//		{
//			throw new IOException(response);
//		}
//	}
	
	public void editPage(WikiRevision rv) throws IOException
	{
		login(rv.getPage().getProject().getUser(rv.getUser()));

		mpp.addParam("format", "xml");
		mpp.addParam("action", "edit");
		mpp.addParam("title", rv.getPage().getTitle());
		mpp.addParam("text", rv.getText());
		mpp.addParam("summary", rv.getComment());
		mpp.addParam("token", edittoken);

		String response = mpp.post(apiurl);
		String result = Util.substring(response, "result=\"", "\"");

		if(!result.equalsIgnoreCase("Success"))
		{
			throw new IOException(response);
		}
	}
	
//	public void uploadImage(WikiImage img) throws IOException
//	{
//		login(img.getPage().getProject().getUser(img.getUser()));
//
//		mpp.addParam("format", "xml");
//		mpp.addParam("action", "upload");
//		mpp.addParam("ignorewarnings", "1");
//		mpp.addParam("filename", img.getPage().getTitleWithoutNamespace());
//		mpp.addParam("comment", img.getComment());
//		mpp.addFile("file", img.getPage().getTitleWithoutNamespace(), img.getStream());
//		mpp.addParam("token", edittoken);
//
//		String response = mpp.post(apiurl);
//		String result = Util.substring(response, "result=\"", "\"");
//
//		if(!result.equalsIgnoreCase("Success"))
//		{
//			throw new IOException(response);
//		}
//	}
	
//	public void uploadPage(File wikiDir, File imageDir, WikiPage p) throws IOException
//	{
//		File image = null;
//		boolean hasContent = false;
//		
//		if(p.getImage().getFileName() != null)
//		{
//			image = new File(imageDir, p.getImage().getFileName());
//			
//			if(!image.exists())
//			{
//				image = null;
//			}
//		}
//		
//		mpp.addParam("format", "xml");
//		
//		if(image != null && image.length() > 0)
//		{
//			FileInputStream fis = new FileInputStream(image);
//			
//			mpp.addParam("action", "upload");
//			mpp.addParam("filename", p.getTitle().substring(p.getTitle().indexOf(':') + 1));
//			mpp.addFile("file", p.getImage().getFileName(), fis);
//			
//			fis.close();
//			
//			hasContent = true;
//		}
//		else
//		{
//			mpp.addParam("action", "edit");
//			mpp.addParam("title", p.getTitle());
//		}
//		
//		if(p.getRevision().getFileName() != null)
//		{
//			File wiki = new File(wikiDir, p.getRevision().getFileName());
//
//			if(wiki.length() > 0)
//			{
//				FileInputStream fis = new FileInputStream(wiki);
//				mpp.addFile("text", null, fis);
//				fis.close();
//				
//				hasContent = true;
//			}
//		}
//		
//		if(hasContent)
//		{
//			mpp.addParam("token", token);
//
//			String response = mpp.post(apiurl);
//			String result = Util.substring(response, "result=\"", "\"");
//
//			if(result == null)
//			{
//				if(response.contains("error"))
//				{
//					String code = Util.substring(response, "code=\"", "\"");
//					String info = Util.substring(response, "info=\"", "\"");
//					
//					throw new WikiException(p, code, info);
//				}
//				
//				throw new IOException(response);
//			}
//
//			if(result.equalsIgnoreCase("Warning"))
//			{
//				throw new WikiException(p, "warning", response);
//			}
//			else if(!result.equalsIgnoreCase("Success"))
//			{
//				throw new IOException(result);
//			}
//		}
//	}
}