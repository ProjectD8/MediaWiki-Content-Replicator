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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;

public class CounterOutputStream extends FilterOutputStream
{
	protected CRC32 crc = new CRC32();
	protected long count = 0;

	public CounterOutputStream(OutputStream out)
	{
		super(out);
	}
	
	public void write(byte[] b) throws IOException
	{
		write(b, 0, b.length);
	}
	
	public void write(byte[] b, int off, int len) throws IOException
	{
		out.write(b, off, len);
		crc.update(b, off, len);
		count += len;
	}
	
	public void write(int b) throws IOException
	{
		out.write(b);
		crc.update(b);
		count++;
	}
	
	public long getChecksum()
	{
		return crc.getValue();
	}
	
	public long getCount()
	{
		return count;
	}
}