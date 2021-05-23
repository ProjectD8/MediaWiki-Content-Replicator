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
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class WikiPage implements Comparable<WikiPage>
{
	public static final int REQUIRES_DOWNLOAD = 0;
	public static final int PARTIALLY_DOWNLOADED = 1;
	public static final int DOWNLOADED = 2;
	
	public static final int INFLATE_ALL = 0;
	public static final int INFLATE_NOT_UPLOADED = 1;
	public static final int INFLATE_LATEST = 2;
	
	public static final int UPLOAD_LINK = 0;
	public static final int UPLOAD_EMBED = 1;
	public static final int UPLOAD_IGNORE = 2;
	
	private Project project;
	
	private int id;
	private int ns;
	private String title;
	
	private long offset = -1;
	private boolean loaded = true;
	
	private final TreeMap<Integer, WikiRevision> revisions = new TreeMap();
	private final TreeMap<String, WikiImage> images = new TreeMap();
	
	private String zipname;
	private final HashMap<String, WikiRevision> entrymap = new HashMap();
	private ZipFile zip;
	private boolean useDefaultZipName; // по назначению не используется, пишется zipname
	
	private boolean missing;
	private boolean actual;
	
	private int containedIn;
	
	public WikiPage(int id, int ns)
	{
		this.id = id;
		this.ns = ns;
		
		missing = false;
		actual = false;
		
		containedIn = -1;
	}
	
	public WikiPage(DataInput dis, int version, ProgressMonitor progress) throws IOException
	{
		read(dis, version, progress);
	}
	
	public void write(DataOutput dos) throws IOException
	{
		dos.writeInt(Project.VERSION);
		
		dos.writeInt(id);
		dos.writeInt(ns);
		
		dos.writeBoolean(missing);
		dos.writeBoolean(actual);
		
		dos.writeInt(containedIn);
		
		Util.writeUTF(dos, title);
		Util.writeUTF(dos, hasZipName() ? getZipName() : null);
		dos.writeBoolean(useDefaultZipName);
		
		dos.writeInt(revisions.size());

		for(WikiRevision rv : revisions.values())
		{
			rv.write(dos);
		}

		dos.writeInt(images.size());

		for(WikiImage img : images.values())
		{
			img.write(dos);
		}
	}
	
	public void read(DataInput dis, int version, ProgressMonitor progress) throws IOException
	{
		if(version >= 13)
		{
			version = dis.readInt();
		}
		
		id = dis.readInt();
		
		if(version >= 8)
		{
			ns = dis.readInt();
		}
		else
		{
			ns = 0;
		}
		
		if(version >= 5)
		{
			missing = dis.readBoolean();
			actual = (version >= 6) ? dis.readBoolean() : true;
			
			containedIn = (version >= 16) ? dis.readInt() : -1;
			
			title = Util.readUTF(dis);
			
			zipname = Util.readUTF(dis);
			useDefaultZipName = version >= 11 ? dis.readBoolean() : false;
			
//			if(useDefaultZipName)
//			{
//				zipname = null;
//			}
			
			int count = dis.readInt();
			
			progress.setOperationProgress(0);
			progress.setOperationLimit(count);

			for(int i = 0; i < count; i++)
			{
				addRevision(new WikiRevision(dis, version));
				progress.progressOperation(1);
			}

			count = dis.readInt();
			
			progress.setOperationLimit(revisions.size() + count);

			for(int i = 0; i < count; i++)
			{
				addImage(new WikiImage(dis, version));
				progress.progressOperation(1);
			}
		}
		else
		{
			missing = (version >= 3) ? dis.readBoolean() : false;
			actual = true;
			
			containedIn = -1;

			if(!missing)
			{
				title = dis.readUTF();

				int count = dis.readInt();

				for(int i = 0; i < count; i++)
				{
					addRevision(new WikiRevision(dis, version));
				}

				count = dis.readInt();

				for(int i = 0; i < count; i++)
				{
					addImage(new WikiImage(dis, version));
				}
			}
		}
		
		loaded = true;
	}

	public long getOffset()
	{
		return offset;
	}

	public void setOffset(long offset)
	{
		this.offset = offset;
		this.loaded = false;
	}
	
	public boolean isLoaded()
	{
		return loaded;
	}
	
	public boolean load(ProgressMonitor progress) throws IOException
	{
		if(loaded || offset < 0)
		{
			return false; // throw new IllegalStateException("nothing to load");
		}
		
		RandomAccessFile dataFile = project.getDataFile();

		dataFile.seek(offset);
		read(dataFile, project.getVersion(), progress);
		
		return true;
	}
	
	public void unload(boolean update) throws IOException
	{
		if(update && loaded)
		{
			RandomAccessFile dataFile = project.getDataFile();

			offset = dataFile.length();

			dataFile.seek(offset);
			write(dataFile);
		}
		
		loaded = false;
		
		closeArchive();
		entrymap.clear();
		zipname = null;
		
		revisions.clear();
		images.clear();
	}
	
	public String getDataFileName()
	{
		return Integer.toString(ns) + File.separator + Integer.toString(id) + ".dat";
	}

	public Project getProject()
	{
		return project;
	}

	public void setProject(Project project)
	{
		this.project = project;
	}

	public int getNS()
	{
		return ns;
	}

	public void setNS(int ns)
	{
		this.ns = ns;
	}
	
	public int getID()
	{
		return id;
	}
	
	public void setTitle(String newTitle)
	{
		if(newTitle != null && !newTitle.isEmpty())
		{
			title = newTitle;
		}
	}
	
	public String getTitle()
	{
		return title != null ? title : "";
	}
	
	public String getTitleWithoutNamespace()
	{
		String namespace = project.getNamespace(ns).getName().trim();

		if(!namespace.isEmpty() && title.startsWith(namespace + ":"))
		{
			return title.substring(namespace.length() + 1);
		}
		else
		{
			return title;
		}
	}
	
	public boolean isContainedIn(WikiPage container, ProgressMonitor progress)
	{
		progress.setPageProgress(0);
		progress.setPageLimit(countRevisions());
		
		if(countRevisions() > container.countRevisions())
		{
			return false;
		}
		
		for(WikiRevision rv : revisions.values())
		{
			if(container.getRevision(rv.getID()) == null)
			{
				return false;
			}
			
			progress.progressPage(1);
		}
		
		return true;
	}
	
	public int isContainedIn()
	{
		return containedIn;
	}
	
	public void setContainedIn(int containerID)
	{
		containedIn = containerID;
	}
	
	public int countDownloadedRevisions()
	{
		int count = 0;
		
		for(WikiRevision rv : revisions.values())
		{
			if(rv.hasEntryName())
			{
				count++;
			}
		}
		
		return count;
	}
	
	public int countRevisions()
	{
		return revisions.size();
	}
	
	public int getDownloadStatus()
	{
		if(revisions.isEmpty() && images.isEmpty())
		{
			return REQUIRES_DOWNLOAD;
		}
		
		for(WikiRevision rv : revisions.values())
		{
			if(!rv.hasEntryName())
			{
				return PARTIALLY_DOWNLOADED;
			}
		}
		
		for(WikiImage img : images.values())
		{
			if(!img.hasFileName())
			{
				return PARTIALLY_DOWNLOADED;
			}
		}
		
		return DOWNLOADED;
	}
	
	public boolean requiresDownload()
	{
		return revisions.isEmpty() && images.isEmpty();
	}
	
	public Collection<WikiRevision> listRevisions()
	{
		return revisions.values();
	}
	
	public int getNewestRevisionID()
	{
		if(revisions.isEmpty())
		{
			return -1;
		}
		else
		{
			// они отсортированы по возрастанию revid, поэтому
			return revisions.lastEntry().getValue().getID();
		}
	}
	
	public WikiRevision getRevision(int id)
	{
		return revisions.get(id);
	}
	
	public void addRevision(WikiRevision rv)
	{
		rv.setPage(this);
		revisions.put(rv.getID(), rv);
		entrymap.put(rv.getEntryName(), rv);
	}
	
	public Collection<WikiImage> listImages()
	{
		return images.values();
	}
	
	public boolean hasImages()
	{
		return !images.isEmpty();
	}
	
	public String getNewestImageTimestamp()
	{
		if(images.isEmpty())
		{
			return null;
		}
		else
		{
			return images.lastEntry().getValue().getTimestamp();
		}
	}
	
	public WikiImage getImage(String timestamp)
	{
		return images.get(timestamp);
	}
	
	public void addImage(WikiImage img)
	{
		img.setPage(this);
		images.put(img.getTimestamp(), img);
	}

	public boolean isMissing()
	{
		return missing;
	}

	public void setMissing(boolean missing)
	{
		this.missing = missing;
	}

	public boolean isActual()
	{
		return actual;
	}

	public void setActual(boolean actual)
	{
		this.actual = actual;
	}
	
	public boolean hasZipName()
	{
		return zipname != null || useDefaultZipName;
	}
	
	public String getDefaultZipName()
	{
		if(title != null)
		{
			String namespace = project.getNamespace(ns).getName().trim();
			String name = title;
			
			if(namespace.isEmpty())
			{
				namespace = "_default";
			}
			else if(name.startsWith(namespace + ":"))
			{
				name = name.substring(namespace.length() + 1);
			}
			
			name = Util.validateFileName(name);
			
			if(!name.isEmpty())
			{
				return namespace + "/" + name.substring(0, 1).toUpperCase() + "/" + name + "." + Integer.toString(id) + ".zip";
			}
			else
			{
				return namespace + "/" + Integer.toString(id) + ".zip";
			}
		}
		else
		{
			return "_missing/" + Integer.toString(id) + ".zip";
		}
	}

	public String getZipName()
	{
		if(zipname != null)
		{
			return zipname;
		}
		
		return getDefaultZipName();
	}

	public void setZipName(String name)
	{
		zipname = name;
		useDefaultZipName = false;
		
//		if(getDefaultZipName().equals(name))
//		{
//			zipname = null;
//			useDefaultZipName = true;
//		}
//		else
//		{
//			zipname = name;
//			useDefaultZipName = false;
//		}
	}
	
//	/**
//	 * Достать текст ревизий из файлов.
//	 * 
//	 * Читает только те ревизии, у которых есть файлы.
//	 * Если ревизий нет вообще, или ни у одной ревизии нет файла,
//	 * ничего не произойдет вообще.
//	 * 
//	 * Перед вызовом никаких дополнительных проверок делать не нужно - все здесь.
//	 * 
//	 * @param dir папка project.getBaseDir() + "wiki/"
//	 * @throws IOException 
//	 */
//	public void readRevisions(File dir) throws IOException
//	{
//		for(WikiRevision rv : revisions.values())
//		{
//			if(rv.hasFileName())
//			{
//				File file = new File(dir, rv.getFileName().replace('/', File.separatorChar));
//				
//				if(file.exists())
//				{
//					InputStream is = new FileInputStream(file);
//					Reader rd = new InputStreamReader(is, "UTF-8");
//
//					char[] cs = new char[0x10000];
//					int len;
//
//					StringBuilder buf = new StringBuilder();
//
//					while(true)
//					{
//						len = rd.read(cs);
//
//						if(len > 0)
//						{
//							buf.append(cs, 0, len);
//						}
//						else if(len < 0)
//						{
//							break;
//						}
//					}
//
//					rd.close();
//					is.close();
//
//					rv.setText(buf.toString());
//					
//					file.delete();
//				}
//				
//				rv.setFileName(null);
//			}
//		}
//	}
	
	public void openArchive(File file) throws IOException
	{
		if(zip == null)
		{
			zip = new ZipFile(file);
		}
	}
	
	public void closeArchive()
	{
		if(zip != null)
		{
			try
			{
				zip.close();
			}
			catch(IOException ex)
			{
			}
		}
		
		zip = null;
	}
	
	public boolean isArchiveOpen()
	{
		return zip != null;
	}
	
	public void inflateRevision(WikiRevision rv) throws IOException
	{
		if(rv.hasEntryName())
		{
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			byte[] buf = new byte[0x10000];
			int len;
			
			ZipEntry entry = zip.getEntry(rv.getEntryName());
			InputStream is = zip.getInputStream(entry);

			while(is.available() > 0)
			{
				len = is.read(buf);

				if(len > 0)
				{
					baos.write(buf, 0, len);
				}
				else if(len < 0)
				{
					break;
				}
			}

			is.close();

			rv.setText(new String(baos.toByteArray(), "UTF-8"));
			rv.setEncodedTextLength(baos.size());
			
			baos.close();
		}
	}
	
	/**
	 * Достать текст ревизий из архива.
	 * 
	 * Читает все ревизии из архива, но обновляет только те, которые есть в проекте.
	 * 
	 * Сюда нужно передать файл, который чтобы создать, надо проверить hasZipName().
	 * Так что прочитать несуществующий файл тоже в принципе не должно получиться.
	 * 
	 * @param file собственно ZIP архив
	 * @param mode что читать: все / не загруженные / только последнюю
	 * @throws IOException 
	 */
	public void inflate(File file, int mode) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		byte[] buf = new byte[0x10000];
		int len;

		if(mode == INFLATE_NOT_UPLOADED ||
		   mode == INFLATE_LATEST)
		{
			openArchive(file);
			
			boolean lastRevOnly = mode == INFLATE_LATEST;
			
			for(WikiRevision rv : revisions.values())
			{
				if(lastRevOnly)
				{
					rv = revisions.lastEntry().getValue();
				}
				
				if(rv.hasEntryName() && (lastRevOnly || !rv.isUploaded()))
				{
					ZipEntry entry = zip.getEntry(rv.getEntryName());
					InputStream is = zip.getInputStream(entry);

					while(is.available() > 0)
					{
						len = is.read(buf);

						if(len > 0)
						{
							baos.write(buf, 0, len);
						}
						else if(len < 0)
						{
							break;
						}
					}

					is.close();

					rv.setText(new String(baos.toByteArray(), "UTF-8"));
					baos.reset();
				}
				
				if(lastRevOnly)
				{
					break;
				}
			}
			
			closeArchive();
		}
		else // по умолчанию читаем все
		{
			ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file), Util.BUFFER_SIZE));
			ZipEntry entry;
			
			while((entry = zis.getNextEntry()) != null)
			{
				WikiRevision rv = entrymap.get(entry.getName());

				if(rv != null)
				{
					while(zis.available() > 0)
					{
						len = zis.read(buf);

						if(len > 0)
						{
							baos.write(buf, 0, len);
						}
						else if(len < 0)
						{
							break;
						}
					}

					rv.setText(new String(baos.toByteArray(), "UTF-8"));
					baos.reset();
				}
			}

			zis.close();
		}
		
		baos.close();
	}
	
	public void deflate(File file) throws IOException
	{
		File dir = file.getParentFile();
		
		if(!dir.exists())
		{
			dir.mkdirs();
		}
		
		ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file), Util.BUFFER_SIZE));
		zos.setLevel(Deflater.BEST_COMPRESSION);
		
		for(WikiRevision rv : revisions.values())
		{
			if(rv.hasText())
			{
				String entryname = rv.getEntryName();
				
				zos.putNextEntry(new ZipEntry(entryname));
				zos.write(rv.getText().getBytes("UTF-8"));
				
				rv.setEntryName(entryname);
				rv.setText(null);
			}
		}
		
		zos.close();
	}
	
	public boolean needsRefactoring()
	{
		for(WikiRevision rv : revisions.values())
		{
			if(rv.needsRefactoring())
			{
				return true;
			}
		}
		
		for(WikiImage img : images.values())
		{
			if(img.needsRefactoring())
			{
				return true;
			}
		}
		
		return hasZipName() && !getDefaultZipName().equals(getZipName());
	}
	
	public void refactor(File oldZip, File newZip, ProgressMonitor progress) throws IOException
	{
		progress.setPageProgress(0);
		progress.setPageLimit(revisions.size());

		openArchive(oldZip);
		
		File dir = newZip.getParentFile();
		
		if(!dir.exists())
		{
			dir.mkdirs();
		}
		
		ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(newZip), Util.BUFFER_SIZE));
		zos.setLevel(Deflater.BEST_COMPRESSION);
		
		for(WikiRevision rv : revisions.values())
		{
			inflateRevision(rv);
			
			if(rv.hasText())
			{
				String entryname = rv.getDefaultEntryName();
				
				zos.putNextEntry(new ZipEntry(entryname));
				zos.write(rv.getText().getBytes("UTF-8"));
				
				rv.setEntryName(entryname);
				rv.setText(null);
			}
			
			progress.progressPage(1);
		}
		
		zos.close();
		
		closeArchive();
	}
	
	public boolean supposedToHaveImages()
	{
		return ns == 6;
	}
	
	public boolean guessImageURLs()
	{
		if(title == null || title.isEmpty())
		{
			return false;
		}
		
		String filename = title;
		String namespace = project.getNamespace(ns).getName() + ":";
		
		if(filename.startsWith(namespace))
		{
			filename = filename.substring(namespace.length());
		}
		
		filename = filename.replace(' ', '_');
		
		String hash = Util.md5(filename);
		hash = hash.substring(0, 1) + "/" + hash.substring(0, 2) + "/";
		
		boolean updated = false;
		
		/*
		 * Первый проход: на каждую правку находим / создаем картинку,
		 * создаем URL для только тех, кто его еще не имеет
		 */
		
		WikiRevision revision = null;
		
		for(WikiRevision nextRevision : revisions.values())
		{
			if(revision != null)
			{
				WikiImage image = images.get(revision.getTimestamp());
				
				if(image == null)
				{
					image = new WikiImage(revision.getTimestamp());
					addImage(image);
					
					image.setUser(revision.getUser());
					image.setComment(revision.getComment());
				}
				
				if(!image.hasURL())
				{
					image.setArchiveName(Util.timestampToURL(nextRevision.getTimestamp()) + "!" + filename);
					image.setURL(project.getBaseURL() + "images/archive/" + hash + Util.encodeURL(image.getArchiveName()));
					
					updated = true;
				}
			}
			
			revision = nextRevision;
		}
		
		if(revision != null)
		{
			WikiImage image = images.get(revision.getTimestamp());

			if(image == null)
			{
				image = new WikiImage(revision.getTimestamp());
				addImage(image);

				image.setUser(revision.getUser());
				image.setComment(revision.getComment());
			}

			if(!image.hasURL())
			{
				image.setURL(project.getBaseURL() + "images/" + hash + Util.encodeURL(filename));
				updated = true;
			}
		}
		
		/*
		 * Второй проход: архивируем все картинки, кроме последней
		 */
		
		WikiImage image = null;
		
		for(WikiImage nextImage : images.values())
		{
			if(image != null && !image.isArchived())
			{
				image.setArchiveName(Util.timestampToURL(nextImage.getTimestamp()) + "!" + filename);
				image.setURL(project.getBaseURL() + "images/archive/" + hash + Util.encodeURL(image.getArchiveName()));

				updated = true;
			}
			
			image = nextImage;
		}
		
		return updated;
	}
	
//	public void clearRevisionsText(boolean markAsUploaded)
//	{
//		if(markAsUploaded)
//		{
//			for(WikiRevision rv : revisions.values())
//			{
//				rv.setText(null);
//				rv.setUploaded(true);
//			}
//		}
//		else
//		{
//			for(WikiRevision rv : revisions.values())
//			{
//				rv.setText(null);
//			}
//		}
//		
//		System.gc();
//	}
	
//	public void closeImageStreams(boolean markAsUploaded)
//	{
//		if(markAsUploaded)
//		{
//			for(WikiImage img : images.values())
//			{
//				img.closeStream();
//				img.setUploaded(true);
//			}
//		}
//		else
//		{
//			for(WikiImage img : images.values())
//			{
//				img.closeStream();
//			}
//		}
//	}
	
	public void setUploaded(boolean uploaded)
	{
		for(WikiRevision rv : revisions.values())
		{
			rv.setUploaded(uploaded);
		}
		
		for(WikiImage img : images.values())
		{
			img.setUploaded(uploaded);
		}
	}
	
	public boolean isUploaded()
	{
		if(revisions.isEmpty() && images.isEmpty())
		{
			return false;
		}
		
		for(WikiRevision rv : revisions.values())
		{
			if(rv.hasEntryName() && !rv.isUploaded())
			{
				return false;
			}
		}
		
		for(WikiImage img : images.values())
		{
			if(img.hasFileName() && !img.isUploaded())
			{
				return false;
			}
		}
		
		return true;
	}
	
	public int countImages()
	{
		return images.size();
	}
	
	public int countDownloadedImages()
	{
		int count = 0;
		
		for(WikiImage img : images.values())
		{
			if(img.hasFileName())
			{
				count++;
			}
		}
		
		return count;
	}
	
	public int countUploadedImages()
	{
		int count = 0;
		
		for(WikiImage img : images.values())
		{
			if(img.isUploaded())
			{
				count++;
			}
		}
		
		return count;
	}
	
	public int dump(XMLStringBuilder xml, int inflateMode, int uploadMode, ProgressMonitor progress) throws IOException
	{
		progress.setPageProgress(0);
		progress.setPageLimit(inflateMode == INFLATE_LATEST ? 2 : revisions.size() + images.size());
		
		boolean opened = false;
		int count = 0;
		
		openArchive(new File(project.getWikiDir(), getZipName().replace('/', File.separatorChar)));
		
		for(WikiRevision rv : revisions.values())
		{
			if(inflateMode == INFLATE_LATEST)
			{
				rv = revisions.lastEntry().getValue();
			}
			
			if(inflateMode != INFLATE_NOT_UPLOADED || !rv.isUploaded())
			{
				if(!opened)
				{
					xml.openTag("page");

					xml.append("title", title);
					xml.append("ns", Integer.toString(ns));
					xml.append("id", Integer.toString(id));
					
					opened = true;
				}
				
				inflateRevision(rv);
				rv.dump(xml);
				
				rv.setText(null);
				xml.flush();
				
				count++;
			}
			
			progress.progressPage(1);
			
			if(inflateMode == INFLATE_LATEST || progress.isCancelled())
			{
				break;
			}
		}
		
		closeArchive();
		
		if(uploadMode == UPLOAD_LINK || uploadMode == UPLOAD_EMBED)
		{
			for(WikiImage img : images.values())
			{
				if(inflateMode == INFLATE_LATEST)
				{
					img = images.lastEntry().getValue();
				}

				if((inflateMode != INFLATE_NOT_UPLOADED || !img.isUploaded()) && img.hasFileName())
				{
					if(!opened)
					{
						xml.openTag("page");

						xml.append("title", title);
						xml.append("ns", Integer.toString(ns));
						xml.append("id", Integer.toString(id));

						opened = true;
					}

					img.dump(xml, uploadMode == UPLOAD_EMBED, progress);
					xml.flush();
				}

				progress.progressPage(1);

				if(inflateMode == INFLATE_LATEST || progress.isCancelled())
				{
					break;
				}
			}
		}
		else
		{
			progress.progressPage(inflateMode == INFLATE_LATEST ? 1 : images.size());
		}
		
		if(opened)
		{
			xml.closeTag();
			xml.flush();
		}
		
		return count;
	}
	
//	public void writeElement(Writer writer, OutputStream out) throws IOException
//	{
//		XMLStringBuilder xml = new XMLStringBuilder(out, "UTF-8");
//				
//		boolean opened = false;
//		
//		for(WikiRevision rv : revisions.values())
//		{
//			if(!rv.isUploaded())
//			{
//				if(!opened)
//				{
//					xml.openTag("mediawiki");
//					xml.openTag("page");
//
//					xml.append("title", title);
//					xml.append("ns", Integer.toString(ns));
//					xml.append("id", Integer.toString(id));
//					
//					opened = true;
//				}
//				
//				rv.dump(xml);
//				xml.flush();
//			}
//		}
//		
//		if(opened)
//		{
//			xml.closeTag();
//			xml.closeTag();
//			
//			xml.flush();
//		}
//	}

	public int compareTo(WikiPage other)
	{
		return getTitle().compareTo(other.getTitle());
	}
	
	public static final int VERIFICATION_OK					= 0x0;
	public static final int VERIFICATION_ERRORS_MASK		= 0xFF;
	
	public static final int VERIFICATION_NOT_DOWNLOADED		= 0x1;
	public static final int VERIFICATION_MISSING_ARCHIVE	= 0x2;
	public static final int VERIFICATION_MISSING_ENTRY		= 0x4;
	public static final int VERIFICATION_MISSING_IMAGE		= 0x8;
	
	public static final int VERIFICATION_FALSE_MISSING		= 0x0100;
	public static final int VERIFICATION_ORPHANED_ARCHIVE	= 0x0200;
	public static final int VERIFICATION_ORPHANED_ENTRY		= 0x0400;
	public static final int VERIFICATION_LEFTOVER_TEMP		= 0x0800;
	public static final int VERIFICATION_TEMP_ARCHIVE_FIX	= 0x1000;
	public static final int VERIFICATION_TEMP_ARCHIVE_FAIL	= 0x2000;
	
	public static final int VERIFICATION_ORPHANED_IMAGE		= 0x010000;
	public static final int VERIFICATION_IMAGE_NAME_FIX		= 0x020000;
	public static final int VERIFICATION_IMAGE_NAME_FAIL	= 0x040000;
	
	public int verify(boolean checkRevisions, boolean checkImages, ProgressMonitor progress)
	{
		progress.setPageProgress(0);
		progress.setPageLimit(revisions.size() + images.size());
		
		int result = VERIFICATION_OK;
		
		if(actual && !missing && requiresDownload())
		{
			result |= VERIFICATION_NOT_DOWNLOADED;
		}
		
		if(checkRevisions)
		{
			String filename = getZipName();

			File file = new File(project.getWikiDir(), filename.replace('/', File.separatorChar));
			File temp = new File(project.getWikiDir(), filename.replace('/', File.separatorChar) + ".tmp");

			if(temp.exists())
			{
				if(file.exists())
				{
					result |= VERIFICATION_LEFTOVER_TEMP;
					temp.delete();
				}
				else
				{
					if(temp.renameTo(file))
					{
						result |= VERIFICATION_TEMP_ARCHIVE_FIX;
					}
					else
					{
						result |= VERIFICATION_TEMP_ARCHIVE_FAIL;
					}
				}
			}

			if(!hasZipName() && file.exists())
			{
				result |= VERIFICATION_ORPHANED_ARCHIVE;
				setZipName(filename);
			}

			if(hasZipName() && !file.exists()) // !revisions.isEmpty() && (zipname == null || !file.exists()))
			{
				result |= VERIFICATION_MISSING_ARCHIVE;
				setZipName(null);
			}

			if(file.exists())
			{
				try
				{
					openArchive(file);

					boolean flag = true;

					for(WikiRevision rv : revisions.values())
					{
						ZipEntry entry = zip.getEntry(rv.getEntryName());

						if(!rv.hasEntryName() && entry != null)
						{
							result |= VERIFICATION_ORPHANED_ENTRY;
							rv.setEntryName(rv.getEntryName());
						}

						if(rv.hasEntryName() && entry == null)
						{
							result |= VERIFICATION_MISSING_ENTRY;

							rv.setEntryName(null);
							flag = false;
						}

						progress.progressPage(1);
					}

					if(flag && missing)
					{
						result |= VERIFICATION_FALSE_MISSING;
						missing = false;
					}

					closeArchive();
				}
				catch(IOException ex)
				{
					result |= VERIFICATION_MISSING_ARCHIVE;
					setZipName(null);
				}
			}

			if(!hasZipName())
			{
				for(WikiRevision rv : revisions.values())
				{
					rv.setEntryName(null);
				}
			}
		}
		else
		{
			progress.progressPage(revisions.size());
		}
		
		if(checkImages)
		{
			WikiImage prev = null;
			
			for(WikiImage img : images.values())
			{
				String currentFileName = img.getFileName();
				String defaultFileName = img.getDefaultFileName();
				
				File currentFile = new File(project.getImageDir(), currentFileName);
				File currentArchiveFile = new File(project.getImageArchiveDir(), currentFileName);
				
				File defaultFile = new File(project.getImageDir(), defaultFileName);
				File defaultArchiveFile = new File(project.getImageArchiveDir(), defaultFileName);
				
				if(!img.hasFileName())
				{
					if(currentFile.isFile() || currentArchiveFile.isFile())
					{
						result |= VERIFICATION_ORPHANED_IMAGE;
						img.setFileName(currentFileName);
					}
					else if(defaultFile.isFile() || defaultArchiveFile.isFile())
					{
						result |= VERIFICATION_ORPHANED_IMAGE;
						img.setFileName(defaultFileName);
					}
				}
				
				if(img.hasFileName() && !(currentFile.isFile() || currentArchiveFile.isFile() || defaultFile.isFile() || defaultArchiveFile.isFile()))
				{
					result |= VERIFICATION_MISSING_IMAGE;
					img.setFileName(null);
				}
				
				if(currentArchiveFile.isFile() && !currentFileName.equals(defaultFileName))
				{
					if(currentArchiveFile.renameTo(defaultArchiveFile))
					{
						result |= VERIFICATION_IMAGE_NAME_FIX;
						img.setFileName(defaultFileName);
					}
					else
					{
						result |= VERIFICATION_IMAGE_NAME_FAIL;
					}
				}
				
				img.setArchiveName(null);
				
				if(img.hasFileName())
				{
					if(prev != null)
					{
						prev.setArchiveName(prev.getDefaultArchiveName());
					}
					
					prev = img;
				}
			}
		}
		else
		{
			progress.progressPage(images.size());
		}
		
		/*
		 * Если просто нашли осиротелый архив, то это не страшно.
		 * Во всех остальных случаях страницу надо перезагрузить.
		 */
		
		if((result & VERIFICATION_ERRORS_MASK) != VERIFICATION_OK)
		{
			actual = false;
		}
		
		return result;
	}
}