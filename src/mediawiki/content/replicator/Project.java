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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;

public class Project
{
	public static final int VERSION = 16;
	
	public static final String INDEX_FILE_NAME = "project.idx";
	public static final String DATA_FILE_NAME = "project.dat";
	
	public static final String IMAGES_ACTUAL_DIR = "actual/";
	public static final String IMAGES_ARCHIVE_DIR = "archive/";
	
	private String baseURL = "";
	private String baseDir = "";
	
	private String targetURL = "";
	private String localSiteRoot = "";
	
	private int version = VERSION;
	
	private final TreeMap<String, WikiUser> users = new TreeMap();
	private final TreeMap<Integer, WikiNamespace> namespaces = new TreeMap();
	private final HashMap<Integer, WikiPage> pages = new HashMap();
	private final TreeMap<Integer, WikiRename> renames = new TreeMap();
	
	private final HashMap<String, WikiPage> pagesByTitle = new HashMap();
	private final HashMap<Integer, ArrayList<WikiPage>> pagesByNamespace = new HashMap();
	private boolean indexed = false;
	
	private File projectDir;
	private File projectBackupDir;
	private File wikiDir;
	private File imageDir;
	private File imageArchiveDir;
	
	private RandomAccessFile dataFile;
	
	public Project(String path)
	{
		setBaseDir(path);
	}
	
	public void write(ProgressMonitor progress)
	{
		ProgressData progressPrevState = new ProgressData(progress);
		
		progress.print("Saving project...");
		progress.initProgress(true, true, true);
		progress.setProjectLimit(8);
		
		try
		{
			closeDataFile();

			progress.progressProject(1);

			getProjectBackupDir().mkdirs();

			for(File file : getProjectBackupDir().listFiles())
			{
				Util.deleteFile(file);
			}

			progress.progressProject(1);

			getProjectDir().mkdirs();

			for(File file : getProjectDir().listFiles())
			{
				Util.moveFile(file, new File(getProjectBackupDir(), file.getName()));
			}

			progress.progressProject(1);

			File oldDataFile = new File(getProjectBackupDir(), DATA_FILE_NAME);
			RandomAccessFile dataIn = oldDataFile.exists() ? new RandomAccessFile(oldDataFile, "r") : null;

			CounterOutputStream counter = new CounterOutputStream(new BufferedOutputStream(new FileOutputStream(new File(getProjectDir(), DATA_FILE_NAME))));
			DataOutputStream dataOut = new DataOutputStream(counter);

			DataOutputStream indexOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(getProjectDir(), INDEX_FILE_NAME))));

			indexOut.writeInt(VERSION);

			indexOut.writeUTF(baseURL);
			indexOut.writeUTF(targetURL);
			indexOut.writeUTF(localSiteRoot);

			progress.setPageProgress(0);
			progress.setPageLimit(users.size());

			indexOut.writeInt(users.size());

			for(WikiUser user : users.values())
			{
				user.write(indexOut);
				progress.progressPage(1);
			}

			progress.progressProject(1);

			progress.setPageProgress(0);
			progress.setPageLimit(namespaces.size());

			indexOut.writeInt(namespaces.size());

			for(WikiNamespace ns : namespaces.values())
			{
				ns.write(indexOut);
				progress.progressPage(1);
			}

			progress.progressProject(1);

			progress.setPageProgress(0);
			progress.setPageLimit(pages.size());

			indexOut.writeInt(pages.size());

			for(WikiPage page : pages.values())
			{
				if(dataIn != null && page.getOffset() >= 0)
				{
					dataIn.seek(page.getOffset());
					page.read(dataIn, version, progress);
				}
				
				long offset = counter.getCount();

				indexOut.writeInt(page.getID());
				indexOut.writeInt(page.getNS());
				indexOut.writeLong(offset);
				indexOut.writeUTF(page.getTitle());

				page.write(dataOut);

				page.unload(false);
				page.setOffset(offset);

				progress.progressPage(1);
			}
			
			progress.progressProject(1);

			progress.setPageProgress(0);
			progress.setPageLimit(renames.size());

			indexOut.writeInt(renames.size());
			
			for(WikiRename rename : renames.values())
			{
				rename.write(indexOut);
				progress.progressPage(1);
			}

			progress.progressProject(1);

			indexOut.close();
			dataOut.close();

			if(dataIn != null)
			{
				dataIn.close();
			}

			progress.progressProject(1);
			progress.println(" OK");
		}
		catch(Throwable ex)
		{
			progress.println(" ERROR");
			progress.showErrMsg(ex);
		}
		
		progressPrevState.restore(progress);
	}
	
	public boolean read(ProgressMonitor progress) throws IOException
	{
		if(readBinary(getProjectDir(), progress))
		{
			return true;
		}
		
		return readBinaryOld(baseDir + "project.dat", progress);
	}
	
	private boolean readBinary(File dir, ProgressMonitor progress) throws IOException
	{
		progress.initProgress(true, true, true);
		progress.setProjectLimit(4);
		
		File file = new File(dir, INDEX_FILE_NAME);

		if(!file.exists())
		{
			return false;
		}

		DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));

		version = dis.readInt();

		if(version > VERSION)
		{
			throw new IOException("incompatible version (" + version + " > " + VERSION + ")");
		}

		baseURL = dis.readUTF();
		targetURL = dis.readUTF();
		
		if(version >= 14)
		{
			localSiteRoot = dis.readUTF();
		}

		int count = dis.readInt();
		
		progress.setPageProgress(0);
		progress.setPageLimit(count);

		for(int i = 0; i < count; i++)
		{
			WikiUser user = new WikiUser(dis, version);

			addUser(user);
			progress.progressPage(1);

			if(progress.isCancelled())
			{
				dis.close();
				return false;
			}
		}

		progress.progressProject(1);

		count = dis.readInt();
		
		progress.setPageProgress(0);
		progress.setPageLimit(count);

		for(int i = 0; i < count; i++)
		{
			WikiNamespace namespace = new WikiNamespace(this, dis, version, progress);

			addNamespace(namespace);
			progress.progressPage(1);

			if(progress.isCancelled())
			{
				dis.close();
				return false;
			}
		}

		progress.progressProject(1);

		count = dis.readInt();
		
		progress.setPageProgress(0);
		progress.setPageLimit(count);

		for(int i = 0; i < count; i++)
		{
			WikiPage page = new WikiPage(dis.readInt(), dis.readInt());
			
			page.setOffset(dis.readLong());
			page.setTitle(dis.readUTF());

			addPage(page);
			progress.progressPage(1);

			if(progress.isCancelled())
			{
				dis.close();
				return false;
			}
		}

		progress.progressProject(1);
		
		if(version >= 15)
		{
			count = dis.readInt();

			progress.setPageProgress(0);
			progress.setPageLimit(count);

			for(int i = 0; i < count; i++)
			{
				WikiRename event = new WikiRename(dis, version);

				addRename(event);
				progress.progressPage(1);

				if(progress.isCancelled())
				{
					dis.close();
					return false;
				}
			}
		}
		
		progress.progressProject(1);

		dis.close();

		return true;
	}
	
	private void writeBinaryOld(String filename) throws IOException
	{
		filename = filename.replace('/', File.separatorChar);
		
		File file = new File(filename);
		File backup = new File(filename + ".bak");
		
		if(file.exists())
		{
			if(backup.exists())
			{
				backup.delete();
			}
			
			file.renameTo(backup);
		}
		
		DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filename), Util.BUFFER_SIZE));
		
		dos.writeInt(VERSION);
		
		dos.writeUTF(baseURL);
		dos.writeUTF(targetURL);
		
		dos.writeInt(users.size());
		
		for(WikiUser u : users.values())
		{
			u.write(dos);
		}
		
		dos.writeInt(namespaces.size());
		
		for(WikiNamespace ns : namespaces.values())
		{
			ns.write(dos);
		}
		
		dos.writeInt(pages.size());
		
		for(WikiPage p : pages.values())
		{
			p.write(dos);
		}
		
		dos.close();
	}
	
	private boolean readBinaryOld(String filename, ProgressMonitor progress) throws IOException
	{
		filename = filename.replace('/', File.separatorChar);
		
		progress.initProgress(true, true, true);
		progress.setProjectLimit(3);
		
		try
		{
			File file = new File(filename);
			
			if(!file.exists())
			{
				return true;
			}
			
			DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filename), Util.BUFFER_SIZE));

			version = dis.readInt();
			
			if(version > VERSION)
			{
				throw new IOException("incompatible version (" + version + " > " + VERSION + ")");
			}
			
			baseURL = dis.readUTF();
			
			if(version >= 10)
			{
				targetURL = dis.readUTF();
			}
			
			int count = dis.readInt();
			progress.setPageLimit(count);
			progress.setPageProgress(0);

			for(int i = 0; i < count; i++)
			{
				WikiUser user = new WikiUser(dis, version);
//				progress.println(user.getName());
				
				addUser(user);
				progress.progressPage(1);
				
				if(progress.isCancelled())
				{
					dis.close();
					return false;
				}
			}
			
			progress.progressProject(1);

			count = dis.readInt();
			progress.setPageLimit(count);
			progress.setPageProgress(0);

			for(int i = 0; i < count; i++)
			{
				WikiNamespace namespace = new WikiNamespace(this, dis, version, progress);
//				progress.println(namespace.getName());
				
				addNamespace(namespace);
				progress.progressPage(1);
				
				if(progress.isCancelled())
				{
					dis.close();
					return false;
				}
			}
			
			progress.progressProject(1);
			
			if(version >= 7)
			{
				count = dis.readInt();
				progress.setPageLimit(count);
				progress.setPageProgress(0);

				for(int i = 0; i < count; i++)
				{
					WikiPage page = new WikiPage(dis, version, progress);
//					progress.println(page.getTitle());
					
					addPage(page);
					progress.progressPage(1);
					
					if(progress.isCancelled())
					{
						dis.close();
						return false;
					}
				}
			}
			
			progress.progressProject(1);

			dis.close();
			
			return true;
		}
		catch(FileNotFoundException ex)
		{
		}
		
		return false;
	}
	
	public int getVersion()
	{
		return version;
	}

	public void setLocalSiteRoot(String path)
	{
		if(path == null || path.isEmpty())
		{
			return;
		}
		
		path = path.replace(File.separatorChar, '/');
		
		if(!path.endsWith("/"))
		{
			path += "/";
		}
		
		localSiteRoot = path;
	}
	
	public String getLocalSiteRoot()
	{
		return localSiteRoot;
	}
	
	public void setTargetURL(String url)
	{
		if(url == null || url.isEmpty())
		{
			return;
		}
		
		if(!url.contains("://"))
		{
			url = "http://" + url;
		}
		
		if(!url.endsWith("/"))
		{
			url += "/";
		}
		
		targetURL = url;
	}
	
	public String getTargetURL()
	{
		return targetURL;
	}
	
	public void setBaseURL(String url)
	{
		if(url == null || url.isEmpty())
		{
			return;
		}
		
		if(!url.contains("://"))
		{
			url = "http://" + url;
		}
		
		if(!url.endsWith("/"))
		{
			url += "/";
		}
		
		baseURL = url;
	}
	
	public String getBaseURL()
	{
		return baseURL;
	}
	
	public void setBaseDir(String dir)
	{
		if(dir == null)
		{
			return;
		}
		
		if(!dir.endsWith("/"))
		{
			dir += "/";
		}
		
		baseDir = dir;
		
		File file = new File(baseDir);
		file.mkdirs();
		
		projectDir = null;
		projectBackupDir = null;
		wikiDir = null;
		imageDir = null;
		imageArchiveDir = null;
	}
	
	public String getBaseDir()
	{
		return baseDir;
	}
	
	public File getProjectDir()
	{
		if(projectDir == null)
		{
			projectDir = new File((getBaseDir() + "project/actual/").replace('/', File.separatorChar));
		}
		
		return projectDir;
	}
	
	public File getProjectBackupDir()
	{
		if(projectBackupDir == null)
		{
			projectBackupDir = new File((getBaseDir() + "project/backup/").replace('/', File.separatorChar));
		}
		
		return projectBackupDir;
	}
	
	public File getWikiDir()
	{
		if(wikiDir == null)
		{
			wikiDir = new File((getBaseDir() + "wiki/").replace('/', File.separatorChar));
		}
		
		return wikiDir;
	}
	
	public File getImageDir()
	{
		if(imageDir == null)
		{
			imageDir = new File((getBaseDir() + "images/" + IMAGES_ACTUAL_DIR).replace('/', File.separatorChar));
		}
		
		return imageDir;
	}
	
	public File getImageArchiveDir()
	{
		if(imageArchiveDir == null)
		{
			imageArchiveDir = new File((getBaseDir() + "images/" + IMAGES_ARCHIVE_DIR).replace('/', File.separatorChar));
		}
		
		return imageArchiveDir;
	}
	
	public File getImageBaseDir()
	{
		return getImageDir().getParentFile();
	}
	
	public RandomAccessFile getDataFile() throws FileNotFoundException
	{
		if(dataFile == null)
		{
			dataFile = new RandomAccessFile(new File(getProjectDir(), DATA_FILE_NAME), "rw");
		}
		
		return dataFile;
	}
	
	public void closeDataFile() throws IOException
	{
		if(dataFile != null)
		{
			dataFile.close();
		}
		
		dataFile = null;
	}
	
	public ArrayList<WikiNamespace> listNamespaces()
	{
		ArrayList<WikiNamespace> list = new ArrayList(namespaces.size());
		
		for(WikiNamespace ns : namespaces.values())
		{
			if(ns.isIncluded())
			{
				list.add(ns);
			}
		}
		
		return list;
	}
	
	public Collection<WikiNamespace> listAllNamespaces()
	{
		return namespaces.values();
	}
	
	public int countNamespaces()
	{
		int count = 0;
		
		for(WikiNamespace ns : namespaces.values())
		{
			if(ns.isIncluded())
			{
				count++;
			}
		}
		
		return count;
	}
	
	public int countAllNamespaces()
	{
		return namespaces.size();
	}
	
	public WikiNamespace getNamespace(int id)
	{
		return namespaces.get(id);
	}
	
	public void addNamespace(WikiNamespace ns)
	{
		namespaces.put(ns.getID(), ns);
	}
	
	public Collection<WikiUser> listUsers()
	{
		return users.values();
	}
	
	public int countUsers()
	{
		return users.size();
	}
	
	public WikiUser getUser(String name)
	{
		return users.get(name);
	}
	
	public void addUser(WikiUser user)
	{
		users.put(user.getName(), user);
	}
	
	public WikiRename getRename(int id)
	{
		return renames.get(id);
	}
	
	public void addRename(WikiRename event)
	{
		renames.put(event.getID(), event);
	}
	
	public Collection<WikiRename> listRenames()
	{
		return renames.values();
	}
	
	public void addPage(WikiPage page)
	{
		WikiPage current = pages.get(page.getID());
		
		if(current == null || current.getNewestRevisionID() < page.getNewestRevisionID())
		{
			pages.put(page.getID(), page);
			invalidateIndex();
			
			page.setProject(this);
		}
	}
	
	public void removePage(int id)
	{
		if(pages.remove(id) != null)
		{
			invalidateIndex();
		}
	}
	
	public WikiPage getPage(int id)
	{
		return pages.get(id);
	}
	
	public WikiPage getPage(String title)
	{
		indexPages();
		return pagesByTitle.get(title);
	}
	
	public Collection<WikiPage> listPages()
	{
		return pages.values();
	}
	
	public ArrayList<WikiPage> listPages(int ns)
	{
		indexPages();
		return pagesByNamespace.get(ns);
	}
	
	public void indexPages()
	{
		if(indexed)
		{
			return;
		}
		
		for(WikiPage page : pages.values())
		{
			pagesByTitle.put(page.getTitle(), page);
			
			ArrayList<WikiPage> list = pagesByNamespace.get(page.getNS());
			
			if(list == null)
			{
				list = new ArrayList();
				pagesByNamespace.put(page.getNS(), list);
			}
			
			list.add(page);
		}
		
		for(ArrayList<WikiPage> list : pagesByNamespace.values())
		{
			Collections.sort(list);
		}
		
		indexed = true;
	}
	
	public void invalidateIndex()
	{
		if(indexed)
		{
			pagesByTitle.clear();
			pagesByNamespace.clear();
		}
		
		indexed = false;
	}
	
	public int countAllPages()
	{
		return pages.size();
	}
	
	public int countPages()
	{
		boolean allns = true;
		
		for(WikiNamespace ns : namespaces.values())
		{
			if(!ns.isIncluded())
			{
				allns = false;
				break;
			}
		}
		
		if(allns)
		{
			return pages.size();
		}
		else
		{
			int count = 0;

			for(WikiNamespace ns : namespaces.values())
			{
				if(ns.isIncluded())
				{
					count += countPages(ns.getID());
				}
			}

			return count;
		}
	}
	
	public int countPages(int ns)
	{
		indexPages();
		
		ArrayList<WikiPage> list = pagesByNamespace.get(ns);
		return list != null ? list.size() : 0;
	}
	
	public boolean requiresDownload()
	{
		for(WikiPage p : pages.values())
		{
			if(p.requiresDownload())
			{
				return true;
			}
		}
		
		return false;
	}
}