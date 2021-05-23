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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

public class ControlPanel extends javax.swing.JFrame implements ProgressMonitor
{
	public static final int LOG_LIMIT = 20480;
	
	protected StringBuilder logBuffer;
	protected PrintWriter stdlog;
	
	protected boolean cancelled;
	protected Process script;
	
	protected File projectDir;
	protected Project project;
	
	protected DefaultListModel namespacesListModel;
	
	/**
	 * Creates new form ControlPanel
	 */
	public ControlPanel()
	{
		initComponents();
		setLocationRelativeTo(null);
		
		logBuffer = new StringBuilder(LOG_LIMIT);
		
		namespacesListModel = new DefaultListModel();
		namespacesList.setModel(namespacesListModel);
		
		initProgress(false, false, false);
		enableControls(true);
	}
	
	public void resetLog()
	{
		logBuffer.setLength(0);
		tfLog.setText(null);
	}
	
	protected void closeLogStreams()
	{
		if(stdlog != null)
		{
			stdlog.close();
		}
	}
	
	public void print(String text)
	{
		synchronized(tfLog)
		{
			int excess = logBuffer.length() + text.length() - LOG_LIMIT;

			if(excess > 0)
			{
				int index = logBuffer.indexOf("\n", excess);

				if(index >= 0)
				{
					logBuffer.delete(0, index + 1);
				}
				else
				{
					logBuffer.delete(0, excess);
				}
			}

			logBuffer.append(text);

			tfLog.setText(logBuffer.toString());
			tfLog.setCaretPosition(logBuffer.length());
		}
		
		if(stdlog != null)
		{
			stdlog.print(text);
		}
		
		updateProgress();
	}

	public void println(String text)
	{
		print(text + "\n");
	}
	
	public void println()
	{
		print("\n");
	}

	public void initProgress(boolean operation, boolean page, boolean project)
	{
		pbOperation.setValue(0);
		pbPage.setValue(0);
		pbProject.setValue(0);
		
		pbOperation.setEnabled(operation);
		pbPage.setEnabled(page);
		pbProject.setEnabled(project);
		
		lOperation.setEnabled(operation);
		lPage.setEnabled(page);
		lProject.setEnabled(project);
		
		updateProgress();
	}

	public boolean isOperationMonitored()
	{
		return pbOperation.isEnabled();
	}

	public boolean isPageMonitored()
	{
		return pbPage.isEnabled();
	}

	public boolean isProjectMonitored()
	{
		return pbProject.isEnabled();
	}
	
	public int getOperationLimit()
	{
		return pbOperation.getMaximum();
	}
	
	public int getOperationProgress()
	{
		return pbOperation.getValue();
	}

	public void setOperationLimit(int limit)
	{
		pbOperation.setMaximum(limit);
		updateProgress();
	}

	public void setOperationProgress(int value)
	{
		pbOperation.setValue(value);
		updateProgress();
	}

	public void progressOperation(int amount)
	{
		pbOperation.setValue(pbOperation.getValue() + amount);
		updateProgress();
	}
	
	public int getPageLimit()
	{
		return pbPage.getMaximum();
	}
	
	public int getPageProgress()
	{
		return pbPage.getValue();
	}

	public void setPageLimit(int limit)
	{
		pbPage.setMaximum(limit);
		updateProgress();
	}

	public void setPageProgress(int value)
	{
		pbPage.setValue(value);
		updateProgress();
	}

	public void progressPage(int amount)
	{
		pbPage.setValue(pbPage.getValue() + amount);
		updateProgress();
	}
	
	public int getProjectLimit()
	{
		return pbProject.getMaximum();
	}
	
	public int getProjectProgress()
	{
		return pbProject.getValue();
	}

	public void setProjectLimit(int limit)
	{
		pbProject.setMaximum(limit);
		updateProgress();
	}

	public void setProjectProgress(int value)
	{
		pbProject.setValue(value);
		updateProgress();
	}

	public void progressProject(int amount)
	{
		pbProject.setValue(pbProject.getValue() + amount);
		updateProgress();
	}
	
	protected void updateProgress()
	{
		long total = Runtime.getRuntime().totalMemory();
		long used = total - Runtime.getRuntime().freeMemory();
		
		pbHeap.setValue((int)(used * 100L / total));
		pbHeap.setToolTipText(Util.formatNumber(used) + " / " + Util.formatNumber(total));
		
		pbOperation.setToolTipText(Util.formatNumber(pbOperation.getValue()) + " / " + Util.formatNumber(pbOperation.getMaximum()));
		pbPage.setToolTipText(Util.formatNumber(pbPage.getValue()) + " / " + Util.formatNumber(pbPage.getMaximum()));
		pbProject.setToolTipText(Util.formatNumber(pbProject.getValue()) + " / " + Util.formatNumber(pbProject.getMaximum()));
	}

	public boolean isCancelled()
	{
		return cancelled;
	}

	public void setCancelled(boolean cancelled)
	{
		this.cancelled = cancelled;
	}
	
	protected void setProjectTitle(String text)
	{
		((TitledBorder)projectPanel.getBorder()).setTitle(text);
		projectPanel.repaint();
	}
	
	protected void enableControls(boolean enable)
	{
		cmdOpen.setEnabled(enable);
		
		tfSourceURL.setEnabled(enable && project != null);
		tfTargetURL.setEnabled(enable && project != null);
		tfLocalSiteRoot.setEnabled(enable && project != null);
		
		if(!enable)
		{
			tabPane.setSelectedComponent(pProgress);
		}
		
		tabPane.setEnabled(enable && project != null);
		
		if(enable && project != null)
		{
			namespacesListModel.clear();
			
			for(WikiNamespace ns : project.listAllNamespaces())
			{
				namespacesListModel.addElement(Integer.toString(ns.getID()) + ": " + ns.getName().trim() + " (" + project.countPages(ns.getID()) + ")");
				
				if(ns.isIncluded())
				{
					int index = namespacesListModel.size() - 1;
					namespacesList.addSelectionInterval(index, index);
				}
			}
		}
		
		// slThreads.setMaximum(Runtime.getRuntime().availableProcessors());
	}
	
	public void showErrMsg(Throwable ex)
	{
		ex.printStackTrace();
		println(ex.toString());
	}
	
	protected void prepareTask(String task)
	{
		resetLog();
		enableControls(false);
		cancelled = false;
		
		if(task != null)
		{
			println(task);
		}
	}
	
	protected void finishTask()
	{
		println("Finished");
		initProgress(false, false, false);
		enableControls(true);
	}

	/**
	 * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of this method is always regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        projectPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        tfSourceURL = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        tfTargetURL = new javax.swing.JTextField();
        cmdOpen = new javax.swing.JButton();
        jLabel7 = new javax.swing.JLabel();
        tfLocalSiteRoot = new javax.swing.JTextField();
        tabPane = new javax.swing.JTabbedPane();
        pProgress = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        lOperation = new javax.swing.JLabel();
        lPage = new javax.swing.JLabel();
        lProject = new javax.swing.JLabel();
        pbOperation = new javax.swing.JProgressBar();
        pbPage = new javax.swing.JProgressBar();
        pbProject = new javax.swing.JProgressBar();
        cmdCancel = new javax.swing.JButton();
        cmdClear = new javax.swing.JButton();
        cmdRunGC = new javax.swing.JButton();
        lHeap = new javax.swing.JLabel();
        pbHeap = new javax.swing.JProgressBar();
        pUsers = new javax.swing.JPanel();
        cmdListUsers = new javax.swing.JButton();
        cmdPrintUserList = new javax.swing.JButton();
        cmdSetUserExistFlags = new javax.swing.JButton();
        cbUserExistFlags = new javax.swing.JComboBox();
        cmdCreateUsers = new javax.swing.JButton();
        pNamespaces = new javax.swing.JPanel();
        cmdListNamespaces = new javax.swing.JButton();
        cmdFixNamespaces = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        namespacesList = new javax.swing.JList();
        cmdSelectNamespaces = new javax.swing.JButton();
        cmdPrintNamespaceList = new javax.swing.JButton();
        pPages = new javax.swing.JPanel();
        cmdListPages = new javax.swing.JButton();
        cmdPrepareUpdate = new javax.swing.JButton();
        cmdGetPages = new javax.swing.JButton();
        cmdVerify = new javax.swing.JButton();
        cmdPrintPageList = new javax.swing.JButton();
        cmdGetPagesWithImageURLs = new javax.swing.JButton();
        cmdFindDuplicatePages = new javax.swing.JButton();
        cmdRemoveDuplicatePages = new javax.swing.JButton();
        pImages = new javax.swing.JPanel();
        cmdPrintImageURLs = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        tfImageURLFind = new javax.swing.JTextField();
        tfImageURLReplace = new javax.swing.JTextField();
        cmdFixImageURLs = new javax.swing.JButton();
        cmdDownloadImages = new javax.swing.JButton();
        cmdVerifyImages = new javax.swing.JButton();
        cmdGuessImageURLs = new javax.swing.JButton();
        pMeta = new javax.swing.JPanel();
        cmdRefactor = new javax.swing.JButton();
        cmdPrintReport = new javax.swing.JButton();
        cmdListRenames = new javax.swing.JButton();
        cmdPrintRenameList = new javax.swing.JButton();
        cmdRenamePages = new javax.swing.JButton();
        pDump = new javax.swing.JPanel();
        cbExportBy = new javax.swing.JComboBox();
        jLabel5 = new javax.swing.JLabel();
        tfPageToExport = new javax.swing.JTextField();
        tfDumpFile = new javax.swing.JTextField();
        cmdExportSinglePage = new javax.swing.JButton();
        cxAllRevisions = new javax.swing.JCheckBox();
        cmdExportAllPages = new javax.swing.JButton();
        cbUploadedFlags = new javax.swing.JComboBox();
        cmdSetUploadedFlags = new javax.swing.JButton();
        jLabel6 = new javax.swing.JLabel();
        lPageStatus = new javax.swing.JLabel();
        cbDumpFormat = new javax.swing.JComboBox();
        cbDumpUploads = new javax.swing.JComboBox();
        cmdImportViaScript = new javax.swing.JButton();
        cmdVerifyUploads = new javax.swing.JButton();

        FormListener formListener = new FormListener();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("MediaWiki Content Replicator");
        addWindowListener(formListener);

        projectPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Project"));

        jLabel1.setText("Source URL:");

        tfSourceURL.addFocusListener(formListener);

        jLabel2.setText("Target URL:");

        tfTargetURL.addFocusListener(formListener);

        cmdOpen.setText("Open");
        cmdOpen.addActionListener(formListener);

        jLabel7.setText("Local Site Root:");

        tfLocalSiteRoot.addFocusListener(formListener);

        javax.swing.GroupLayout projectPanelLayout = new javax.swing.GroupLayout(projectPanel);
        projectPanel.setLayout(projectPanelLayout);
        projectPanelLayout.setHorizontalGroup(
            projectPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(projectPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(projectPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel2, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel7, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(projectPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tfSourceURL)
                    .addComponent(tfTargetURL)
                    .addComponent(tfLocalSiteRoot))
                .addContainerGap())
            .addGroup(projectPanelLayout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addComponent(cmdOpen, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(12, 12, 12))
        );
        projectPanelLayout.setVerticalGroup(
            projectPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(projectPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(projectPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(tfSourceURL, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(projectPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(tfTargetURL, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(projectPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(tfLocalSiteRoot, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdOpen)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tfLog.setColumns(20);
        tfLog.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        tfLog.setLineWrap(true);
        tfLog.setRows(5);
        tfLog.setWrapStyleWord(true);
        jScrollPane1.setViewportView(tfLog);

        lOperation.setText("Operation:");

        lPage.setText("Page:");

        lProject.setText("Project:");

        cmdCancel.setText("Cancel");
        cmdCancel.addActionListener(formListener);

        cmdClear.setText("Clear");
        cmdClear.addActionListener(formListener);

        cmdRunGC.setText("Run GC");
        cmdRunGC.addActionListener(formListener);

        lHeap.setText("Heap:");

        javax.swing.GroupLayout pProgressLayout = new javax.swing.GroupLayout(pProgress);
        pProgress.setLayout(pProgressLayout);
        pProgressLayout.setHorizontalGroup(
            pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pProgressLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addGroup(pProgressLayout.createSequentialGroup()
                        .addGroup(pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lOperation, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lPage, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lProject, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lHeap, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(pbOperation, javax.swing.GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE)
                            .addComponent(pbPage, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(pbProject, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(pbHeap, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(pProgressLayout.createSequentialGroup()
                        .addComponent(cmdCancel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cmdClear)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cmdRunGC)))
                .addContainerGap())
        );
        pProgressLayout.setVerticalGroup(
            pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pProgressLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 405, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(pProgressLayout.createSequentialGroup()
                        .addGroup(pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lOperation)
                            .addComponent(pbOperation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lPage))
                    .addComponent(pbPage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(lProject)
                    .addComponent(pbProject, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(lHeap)
                    .addComponent(pbHeap, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pProgressLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmdCancel)
                    .addComponent(cmdClear)
                    .addComponent(cmdRunGC))
                .addContainerGap())
        );

        tabPane.addTab("Progress", pProgress);

        cmdListUsers.setText("List Users");
        cmdListUsers.addActionListener(formListener);

        cmdPrintUserList.setText("Print User List");
        cmdPrintUserList.addActionListener(formListener);

        cmdSetUserExistFlags.setText("Mark All Users");
        cmdSetUserExistFlags.addActionListener(formListener);

        cbUserExistFlags.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "as not created", "as created" }));

        cmdCreateUsers.setText("Create Users");
        cmdCreateUsers.addActionListener(formListener);

        javax.swing.GroupLayout pUsersLayout = new javax.swing.GroupLayout(pUsers);
        pUsers.setLayout(pUsersLayout);
        pUsersLayout.setHorizontalGroup(
            pUsersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pUsersLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pUsersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmdListUsers, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdPrintUserList, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(pUsersLayout.createSequentialGroup()
                        .addComponent(cmdSetUserExistFlags, javax.swing.GroupLayout.DEFAULT_SIZE, 244, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(cbUserExistFlags, 0, 242, Short.MAX_VALUE))
                    .addComponent(cmdCreateUsers, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pUsersLayout.setVerticalGroup(
            pUsersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pUsersLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(cmdListUsers)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdPrintUserList)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pUsersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmdSetUserExistFlags)
                    .addComponent(cbUserExistFlags, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdCreateUsers)
                .addContainerGap(408, Short.MAX_VALUE))
        );

        tabPane.addTab("Users", pUsers);

        cmdListNamespaces.setText("List Namespaces");
        cmdListNamespaces.addActionListener(formListener);

        cmdFixNamespaces.setText("Fix NSIDs for pages");
        cmdFixNamespaces.addActionListener(formListener);

        jScrollPane2.setViewportView(namespacesList);

        cmdSelectNamespaces.setText("Select Namespaces");
        cmdSelectNamespaces.addActionListener(formListener);

        cmdPrintNamespaceList.setText("Print Namespace List");
        cmdPrintNamespaceList.addActionListener(formListener);

        javax.swing.GroupLayout pNamespacesLayout = new javax.swing.GroupLayout(pNamespaces);
        pNamespaces.setLayout(pNamespacesLayout);
        pNamespacesLayout.setHorizontalGroup(
            pNamespacesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pNamespacesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pNamespacesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmdListNamespaces, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdFixNamespaces, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 498, Short.MAX_VALUE)
                    .addComponent(cmdSelectNamespaces, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdPrintNamespaceList, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pNamespacesLayout.setVerticalGroup(
            pNamespacesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pNamespacesLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(cmdListNamespaces)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdPrintNamespaceList)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 384, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdSelectNamespaces)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdFixNamespaces)
                .addContainerGap())
        );

        tabPane.addTab("Namespaces", pNamespaces);

        cmdListPages.setText("List Pages");
        cmdListPages.addActionListener(formListener);

        cmdPrepareUpdate.setText("Prepare Update");
        cmdPrepareUpdate.addActionListener(formListener);

        cmdGetPages.setText("Get Pages Only");
        cmdGetPages.addActionListener(formListener);

        cmdVerify.setText("Verify Pages");
        cmdVerify.addActionListener(formListener);

        cmdPrintPageList.setText("Print Page List");
        cmdPrintPageList.addActionListener(formListener);

        cmdGetPagesWithImageURLs.setText("Get Pages and Image URLs");
        cmdGetPagesWithImageURLs.addActionListener(formListener);

        cmdFindDuplicatePages.setText("Find Duplicates");
        cmdFindDuplicatePages.addActionListener(formListener);

        cmdRemoveDuplicatePages.setText("Remove Duplicates");
        cmdRemoveDuplicatePages.addActionListener(formListener);

        javax.swing.GroupLayout pPagesLayout = new javax.swing.GroupLayout(pPages);
        pPages.setLayout(pPagesLayout);
        pPagesLayout.setHorizontalGroup(
            pPagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pPagesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pPagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmdListPages, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdPrepareUpdate, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdVerify, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdPrintPageList, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(pPagesLayout.createSequentialGroup()
                        .addComponent(cmdGetPages, javax.swing.GroupLayout.DEFAULT_SIZE, 243, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(cmdGetPagesWithImageURLs, javax.swing.GroupLayout.DEFAULT_SIZE, 243, Short.MAX_VALUE))
                    .addComponent(cmdFindDuplicatePages, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdRemoveDuplicatePages, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pPagesLayout.setVerticalGroup(
            pPagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pPagesLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(cmdListPages)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdPrintPageList)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdPrepareUpdate)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pPagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmdGetPages)
                    .addComponent(cmdGetPagesWithImageURLs))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdVerify)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdFindDuplicatePages)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdRemoveDuplicatePages)
                .addContainerGap(297, Short.MAX_VALUE))
        );

        tabPane.addTab("Pages", pPages);

        cmdPrintImageURLs.setText("Print Image URLs");
        cmdPrintImageURLs.addActionListener(formListener);

        jLabel3.setText("Find Text:");

        jLabel4.setText("Replace With:");

        cmdFixImageURLs.setText("Fix Image URLs");
        cmdFixImageURLs.addActionListener(formListener);

        cmdDownloadImages.setText("Download Images");
        cmdDownloadImages.addActionListener(formListener);

        cmdVerifyImages.setText("Verify Images");
        cmdVerifyImages.addActionListener(formListener);

        cmdGuessImageURLs.setText("Guess Image URLs");
        cmdGuessImageURLs.addActionListener(formListener);

        javax.swing.GroupLayout pImagesLayout = new javax.swing.GroupLayout(pImages);
        pImages.setLayout(pImagesLayout);
        pImagesLayout.setHorizontalGroup(
            pImagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pImagesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pImagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmdFixImageURLs, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdPrintImageURLs, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(pImagesLayout.createSequentialGroup()
                        .addGroup(pImagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel4, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(pImagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tfImageURLFind)
                            .addComponent(tfImageURLReplace)))
                    .addComponent(cmdDownloadImages, javax.swing.GroupLayout.DEFAULT_SIZE, 498, Short.MAX_VALUE)
                    .addComponent(cmdVerifyImages, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdGuessImageURLs, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pImagesLayout.setVerticalGroup(
            pImagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pImagesLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(cmdGuessImageURLs)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdPrintImageURLs)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pImagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(tfImageURLFind, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pImagesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(tfImageURLReplace, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdFixImageURLs)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdDownloadImages)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdVerifyImages)
                .addContainerGap(309, Short.MAX_VALUE))
        );

        tabPane.addTab("Images", pImages);

        cmdRefactor.setText("Refactor Project");
        cmdRefactor.addActionListener(formListener);

        cmdPrintReport.setText("Print Report");
        cmdPrintReport.addActionListener(formListener);

        cmdListRenames.setText("List Renames");
        cmdListRenames.addActionListener(formListener);

        cmdPrintRenameList.setText("Print Rename List");
        cmdPrintRenameList.addActionListener(formListener);

        cmdRenamePages.setText("Rename Pages");
        cmdRenamePages.addActionListener(formListener);

        javax.swing.GroupLayout pMetaLayout = new javax.swing.GroupLayout(pMeta);
        pMeta.setLayout(pMetaLayout);
        pMetaLayout.setHorizontalGroup(
            pMetaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pMetaLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pMetaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmdRefactor, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdPrintReport, javax.swing.GroupLayout.DEFAULT_SIZE, 498, Short.MAX_VALUE)
                    .addComponent(cmdListRenames, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdPrintRenameList, javax.swing.GroupLayout.DEFAULT_SIZE, 498, Short.MAX_VALUE)
                    .addComponent(cmdRenamePages, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pMetaLayout.setVerticalGroup(
            pMetaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pMetaLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(cmdListRenames)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdPrintRenameList)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdRenamePages)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdRefactor)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdPrintReport)
                .addContainerGap(371, Short.MAX_VALUE))
        );

        tabPane.addTab("Meta", pMeta);

        cbExportBy.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "by title", "by ID" }));
        cbExportBy.addActionListener(formListener);

        jLabel5.setText("Dump File:");

        tfPageToExport.addFocusListener(formListener);

        tfDumpFile.setToolTipText("double-click to browse for a file");
        tfDumpFile.addMouseListener(formListener);

        cmdExportSinglePage.setText("Export Single Page");
        cmdExportSinglePage.addActionListener(formListener);

        cxAllRevisions.setSelected(true);
        cxAllRevisions.setText("All Revisions");

        cmdExportAllPages.setText("Export All Pages");
        cmdExportAllPages.addActionListener(formListener);

        cbUploadedFlags.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "as not uploaded", "as uploaded" }));

        cmdSetUploadedFlags.setText("Mark All Pages");
        cmdSetUploadedFlags.addActionListener(formListener);

        jLabel6.setText("Page:");

        lPageStatus.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lPageStatus.setText("?");

        cbDumpFormat.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "plain", "compressed", "dry run" }));
        cbDumpFormat.setSelectedIndex(1);

        cbDumpUploads.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Link upload files relative to dump location", "Embed Base64-encoded uploads into XML dump", "Do not include uploads" }));

        cmdImportViaScript.setText("Import To Local Site");
        cmdImportViaScript.addActionListener(formListener);

        cmdVerifyUploads.setText("Verify Uploads");
        cmdVerifyUploads.addActionListener(formListener);

        javax.swing.GroupLayout pDumpLayout = new javax.swing.GroupLayout(pDump);
        pDump.setLayout(pDumpLayout);
        pDumpLayout.setHorizontalGroup(
            pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pDumpLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmdExportAllPages, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cbDumpUploads, 0, 498, Short.MAX_VALUE)
                    .addGroup(pDumpLayout.createSequentialGroup()
                        .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabel5)
                            .addGroup(pDumpLayout.createSequentialGroup()
                                .addComponent(lPageStatus, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel6)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tfDumpFile)
                            .addComponent(tfPageToExport))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(cbDumpFormat, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cxAllRevisions)))
                    .addGroup(pDumpLayout.createSequentialGroup()
                        .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(cmdExportSinglePage, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(cmdSetUploadedFlags, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(cbUploadedFlags, 0, 318, Short.MAX_VALUE)
                            .addComponent(cbExportBy, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addComponent(cmdImportViaScript, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cmdVerifyUploads, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pDumpLayout.setVerticalGroup(
            pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pDumpLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmdSetUploadedFlags)
                    .addComponent(cbUploadedFlags, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmdExportSinglePage)
                    .addComponent(cbExportBy, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cbDumpUploads, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(tfPageToExport, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lPageStatus)
                    .addComponent(cxAllRevisions))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pDumpLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(tfDumpFile, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cbDumpFormat, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdExportAllPages)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdImportViaScript)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(cmdVerifyUploads)
                .addContainerGap(268, Short.MAX_VALUE))
        );

        tabPane.addTab("Dump", pDump);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(projectPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tabPane))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(projectPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tabPane)
                .addContainerGap())
        );

        pack();
    }

    // Code for dispatching events from components to event handlers.

    private class FormListener implements java.awt.event.ActionListener, java.awt.event.FocusListener, java.awt.event.MouseListener, java.awt.event.WindowListener
    {
        FormListener() {}
        public void actionPerformed(java.awt.event.ActionEvent evt)
        {
            if (evt.getSource() == cmdOpen)
            {
                ControlPanel.this.cmdOpenActionPerformed(evt);
            }
            else if (evt.getSource() == cmdCancel)
            {
                ControlPanel.this.cmdCancelActionPerformed(evt);
            }
            else if (evt.getSource() == cmdClear)
            {
                ControlPanel.this.cmdClearActionPerformed(evt);
            }
            else if (evt.getSource() == cmdRunGC)
            {
                ControlPanel.this.cmdRunGCActionPerformed(evt);
            }
            else if (evt.getSource() == cmdListUsers)
            {
                ControlPanel.this.cmdListUsersActionPerformed(evt);
            }
            else if (evt.getSource() == cmdPrintUserList)
            {
                ControlPanel.this.cmdPrintUserListActionPerformed(evt);
            }
            else if (evt.getSource() == cmdSetUserExistFlags)
            {
                ControlPanel.this.cmdSetUserExistFlagsActionPerformed(evt);
            }
            else if (evt.getSource() == cmdCreateUsers)
            {
                ControlPanel.this.cmdCreateUsersActionPerformed(evt);
            }
            else if (evt.getSource() == cmdListNamespaces)
            {
                ControlPanel.this.cmdListNamespacesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdFixNamespaces)
            {
                ControlPanel.this.cmdFixNamespacesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdSelectNamespaces)
            {
                ControlPanel.this.cmdSelectNamespacesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdPrintNamespaceList)
            {
                ControlPanel.this.cmdPrintNamespaceListActionPerformed(evt);
            }
            else if (evt.getSource() == cmdListPages)
            {
                ControlPanel.this.cmdListPagesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdPrepareUpdate)
            {
                ControlPanel.this.cmdPrepareUpdateActionPerformed(evt);
            }
            else if (evt.getSource() == cmdGetPages)
            {
                ControlPanel.this.cmdGetPagesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdVerify)
            {
                ControlPanel.this.cmdVerifyActionPerformed(evt);
            }
            else if (evt.getSource() == cmdPrintPageList)
            {
                ControlPanel.this.cmdPrintPageListActionPerformed(evt);
            }
            else if (evt.getSource() == cmdGetPagesWithImageURLs)
            {
                ControlPanel.this.cmdGetPagesWithImageURLsActionPerformed(evt);
            }
            else if (evt.getSource() == cmdFindDuplicatePages)
            {
                ControlPanel.this.cmdFindDuplicatePagesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdPrintImageURLs)
            {
                ControlPanel.this.cmdPrintImageURLsActionPerformed(evt);
            }
            else if (evt.getSource() == cmdFixImageURLs)
            {
                ControlPanel.this.cmdFixImageURLsActionPerformed(evt);
            }
            else if (evt.getSource() == cmdDownloadImages)
            {
                ControlPanel.this.cmdDownloadImagesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdVerifyImages)
            {
                ControlPanel.this.cmdVerifyImagesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdGuessImageURLs)
            {
                ControlPanel.this.cmdGuessImageURLsActionPerformed(evt);
            }
            else if (evt.getSource() == cmdRefactor)
            {
                ControlPanel.this.cmdRefactorActionPerformed(evt);
            }
            else if (evt.getSource() == cmdPrintReport)
            {
                ControlPanel.this.cmdPrintReportActionPerformed(evt);
            }
            else if (evt.getSource() == cmdListRenames)
            {
                ControlPanel.this.cmdListRenamesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdPrintRenameList)
            {
                ControlPanel.this.cmdPrintRenameListActionPerformed(evt);
            }
            else if (evt.getSource() == cmdRenamePages)
            {
                ControlPanel.this.cmdRenamePagesActionPerformed(evt);
            }
            else if (evt.getSource() == cbExportBy)
            {
                ControlPanel.this.cbExportByActionPerformed(evt);
            }
            else if (evt.getSource() == cmdExportSinglePage)
            {
                ControlPanel.this.cmdExportSinglePageActionPerformed(evt);
            }
            else if (evt.getSource() == cmdExportAllPages)
            {
                ControlPanel.this.cmdExportAllPagesActionPerformed(evt);
            }
            else if (evt.getSource() == cmdSetUploadedFlags)
            {
                ControlPanel.this.cmdSetUploadedFlagsActionPerformed(evt);
            }
            else if (evt.getSource() == cmdImportViaScript)
            {
                ControlPanel.this.cmdImportViaScriptActionPerformed(evt);
            }
            else if (evt.getSource() == cmdVerifyUploads)
            {
                ControlPanel.this.cmdVerifyUploadsActionPerformed(evt);
            }
            else if (evt.getSource() == cmdRemoveDuplicatePages)
            {
                ControlPanel.this.cmdRemoveDuplicatePagesActionPerformed(evt);
            }
        }

        public void focusGained(java.awt.event.FocusEvent evt)
        {
            if (evt.getSource() == tfSourceURL)
            {
                ControlPanel.this.tfSourceURLFocusGained(evt);
            }
            else if (evt.getSource() == tfTargetURL)
            {
                ControlPanel.this.tfTargetURLFocusGained(evt);
            }
            else if (evt.getSource() == tfLocalSiteRoot)
            {
                ControlPanel.this.tfLocalSiteRootFocusGained(evt);
            }
        }

        public void focusLost(java.awt.event.FocusEvent evt)
        {
            if (evt.getSource() == tfSourceURL)
            {
                ControlPanel.this.tfSourceURLFocusLost(evt);
            }
            else if (evt.getSource() == tfTargetURL)
            {
                ControlPanel.this.tfTargetURLFocusLost(evt);
            }
            else if (evt.getSource() == tfLocalSiteRoot)
            {
                ControlPanel.this.tfLocalSiteRootFocusLost(evt);
            }
            else if (evt.getSource() == tfPageToExport)
            {
                ControlPanel.this.tfPageToExportFocusLost(evt);
            }
        }

        public void mouseClicked(java.awt.event.MouseEvent evt)
        {
            if (evt.getSource() == tfDumpFile)
            {
                ControlPanel.this.tfDumpFileMouseClicked(evt);
            }
        }

        public void mouseEntered(java.awt.event.MouseEvent evt)
        {
        }

        public void mouseExited(java.awt.event.MouseEvent evt)
        {
        }

        public void mousePressed(java.awt.event.MouseEvent evt)
        {
        }

        public void mouseReleased(java.awt.event.MouseEvent evt)
        {
        }

        public void windowActivated(java.awt.event.WindowEvent evt)
        {
        }

        public void windowClosed(java.awt.event.WindowEvent evt)
        {
        }

        public void windowClosing(java.awt.event.WindowEvent evt)
        {
            if (evt.getSource() == ControlPanel.this)
            {
                ControlPanel.this.formWindowClosing(evt);
            }
        }

        public void windowDeactivated(java.awt.event.WindowEvent evt)
        {
        }

        public void windowDeiconified(java.awt.event.WindowEvent evt)
        {
        }

        public void windowIconified(java.awt.event.WindowEvent evt)
        {
        }

        public void windowOpened(java.awt.event.WindowEvent evt)
        {
        }
    }// </editor-fold>//GEN-END:initComponents

    private void cmdOpenActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdOpenActionPerformed
    {//GEN-HEADEREND:event_cmdOpenActionPerformed
		JFileChooser chooser = new JFileChooser(projectDir);
		
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int result = chooser.showOpenDialog(this);
		
		if(result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null)
		{
			projectDir = chooser.getSelectedFile();
			
			setProjectTitle(projectDir.getName());
			
			closeLogStreams();
			
			File dir = new File(projectDir, "logs");
			dir.mkdirs();
			
			String filename = Long.toString(System.currentTimeMillis());
			
			try
			{
				stdlog = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, filename + ".log")), "UTF-8")));
			}
			catch(Throwable ex)
			{
				ex.printStackTrace();
				stdlog = null;
			}
			
			Runnable task = new Runnable()
			{
				public void run()
				{
					prepareTask("Reading project...");
					
					project = new Project(projectDir.getAbsolutePath());
					
					try
					{
						if(project.read(ControlPanel.this))
						{
							tfSourceURL.setText(project.getBaseURL());
							tfTargetURL.setText(project.getTargetURL());
							tfLocalSiteRoot.setText(project.getLocalSiteRoot());
						}
						else
						{
							project = null;
							setProjectTitle("Project");
						}
					}
					catch(Throwable ex)
					{
						showErrMsg(ex);
					}
					
					finishTask();
				}
			};

			(new Thread(task)).start();
		}
    }//GEN-LAST:event_cmdOpenActionPerformed

    private void cmdCancelActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdCancelActionPerformed
    {//GEN-HEADEREND:event_cmdCancelActionPerformed
		if(!cancelled)
		{
			println("Cancelling...");
			cancelled = true;
		}
		else if(script != null)
		{
			script.destroy();
		}
    }//GEN-LAST:event_cmdCancelActionPerformed

    private void tfSourceURLFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tfSourceURLFocusLost
    {//GEN-HEADEREND:event_tfSourceURLFocusLost
		if(project != null)
		{
			String prevURL = project.getBaseURL();
			
			project.setBaseURL(tfSourceURL.getText());
			tfSourceURL.setText(project.getBaseURL());
			
			if(prevURL == null || !prevURL.equals(project.getBaseURL()))
			{
				Runnable task = new Runnable()
				{
					public void run()
					{
						prepareTask(null);

						try
						{
							project.write(ControlPanel.this);
						}
						catch(Throwable ex)
						{
							showErrMsg(ex);
						}

						finishTask();
					}
				};

				(new Thread(task)).start();
			}
		}
    }//GEN-LAST:event_tfSourceURLFocusLost

    private void tfTargetURLFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tfTargetURLFocusLost
    {//GEN-HEADEREND:event_tfTargetURLFocusLost
		if(project != null)
		{
			String prevURL = project.getTargetURL();
			
			project.setTargetURL(tfTargetURL.getText());
			tfTargetURL.setText(project.getTargetURL());
			
			if(prevURL == null || !prevURL.equals(project.getTargetURL()))
			{
				Runnable task = new Runnable()
				{
					public void run()
					{
						prepareTask(null);

						try
						{
							project.write(ControlPanel.this);
						}
						catch(Throwable ex)
						{
							showErrMsg(ex);
						}

						finishTask();
					}
				};

				(new Thread(task)).start();
			}
		}
    }//GEN-LAST:event_tfTargetURLFocusLost

    private void tfSourceURLFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tfSourceURLFocusGained
    {//GEN-HEADEREND:event_tfSourceURLFocusGained
		tfSourceURL.selectAll();
    }//GEN-LAST:event_tfSourceURLFocusGained

    private void tfTargetURLFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tfTargetURLFocusGained
    {//GEN-HEADEREND:event_tfTargetURLFocusGained
		tfTargetURL.selectAll();
    }//GEN-LAST:event_tfTargetURLFocusGained

    private void cmdListUsersActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdListUsersActionPerformed
    {//GEN-HEADEREND:event_cmdListUsersActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Requesting user list...");
				
				try
				{
					AllUsersListParser.getAllUsersList(project, ControlPanel.this);

					project.write(ControlPanel.this);

					println("Printing list...");

					OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "users.ini"), Util.BUFFER_SIZE);
					PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

					for(WikiUser u : project.listUsers())
					{
						ps.println(u.getName() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + u.getPassword());
					}

					ps.close();
					os.close();
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdListUsersActionPerformed

    private void cmdListNamespacesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdListNamespacesActionPerformed
    {//GEN-HEADEREND:event_cmdListNamespacesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Requesting namespace list...");
				
				try
				{
					NamespaceParser.getNamespaces(project);

					project.write(ControlPanel.this);

					println("Printing list...");

					OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "namespaces.ini"), Util.BUFFER_SIZE);
					PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

					for(WikiNamespace ns : project.listAllNamespaces())
					{
						ps.println(ns.getID() + "\t=\t" + ns.getName());
					}

					ps.println();

					for(WikiNamespace ns : project.listAllNamespaces())
					{
						if(ns.getID() >= 100)
						{
							ps.println("$wgExtraNamespaces[" + ns.getID() + "] = \"" + ns.getName().replace(' ', '_') + "\";");
						}
					}

					ps.close();
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdListNamespacesActionPerformed

    private void cmdSelectNamespacesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdSelectNamespacesActionPerformed
    {//GEN-HEADEREND:event_cmdSelectNamespacesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Selecting namespaces...");
				
				try
				{
					WikiNamespace[] namespaces = project.listAllNamespaces().toArray(new WikiNamespace[0]);

					for(int i = 0; i < namespaces.length; i++)
					{
						namespaces[i].setIncluded(namespacesList.isSelectedIndex(i));
					}

					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdSelectNamespacesActionPerformed

    private void cmdFixNamespacesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdFixNamespacesActionPerformed
    {//GEN-HEADEREND:event_cmdFixNamespacesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Fixing NSIDs for pages...");
				
				try
				{
					initProgress(true, false, true);
					setProjectLimit(project.countAllPages());
					
					Collection<WikiNamespace> namespaces = project.listAllNamespaces();

					for(WikiPage page : project.listPages())
					{
						page.load(ControlPanel.this);
						
						page.setNS(0);

						String title = page.getTitle();
						int index = title.indexOf(':');

						if(index > 0)
						{
							title = title.substring(0, index);

							for(WikiNamespace ns : namespaces)
							{
								if(title.equals(ns.getName()))
								{
									page.setNS(ns.getID());
									break;
								}
							}
						}
						
						page.unload(true);
						
						progressProject(1);
						
						if(isCancelled())
						{
							break;
						}
					}

					project.invalidateIndex();

					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdFixNamespacesActionPerformed

    private void cmdListPagesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdListPagesActionPerformed
    {//GEN-HEADEREND:event_cmdListPagesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Requesting page list...");
				
				try
				{
					AllPagesListParser.getAllPagesList(project, ControlPanel.this);

					project.write(ControlPanel.this);

					println("Printing list...");

					OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "pages.ini"), Util.BUFFER_SIZE);
					PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

					for(WikiNamespace ns : project.listNamespaces())
					{
						ps.println(IniEntry.SECTION_START + ns.getID() + ":" + ns.getName() + IniEntry.SECTION_END);
						ps.println();

						ArrayList<WikiPage> pages = project.listPages(ns.getID());

						if(pages != null)
						{
							for(WikiPage p : pages)
							{
								ps.println(p.getTitle() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + p.getID());
							}

							ps.println();
						}
					}

					ps.close();
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdListPagesActionPerformed

    private void cmdPrepareUpdateActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdPrepareUpdateActionPerformed
    {//GEN-HEADEREND:event_cmdPrepareUpdateActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Preparing update...");
				
				try
				{
					UpdateListParser.markForUpdates(project, ControlPanel.this, Util.pagesPerRequest, Util.projectSaveInterval);
					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdPrepareUpdateActionPerformed

    private void cmdGetPagesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdGetPagesActionPerformed
    {//GEN-HEADEREND:event_cmdGetPagesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Downloading pages...");
				
				try
				{
					PageContentParser.getPagesContents(project, ControlPanel.this, Util.pagesPerRequest, Util.projectSaveInterval, false);
					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdGetPagesActionPerformed

	protected void verifyPages(boolean checkRevisions, boolean checkImages)
	{
		try
		{
			initProgress(true, true, true);
			setProjectLimit(project.countPages());

			long currentTime = System.currentTimeMillis();
			long projectSaveTime = currentTime + Util.projectSaveInterval;

			for(WikiNamespace ns : project.listNamespaces())
			{
				println("Processing namespace " + ns.getID() + " (" + ns.getName() + ")...");

				ArrayList<WikiPage> pages = project.listPages(ns.getID());

				if(pages == null)
				{
					continue;
				}

				for(WikiPage page : pages)
				{
					page.load(ControlPanel.this);

					int result = page.verify(checkRevisions, checkImages, ControlPanel.this);

					if(result != WikiPage.VERIFICATION_OK)
					{
						println("[" + Integer.toHexString(result) + "] " + page.getTitle());
						page.unload(true);
					}
					else
					{
						page.unload(checkImages);
					}

					progressProject(1);

					currentTime = System.currentTimeMillis();

					if(isCancelled())
					{
						break;
					}
					else if(currentTime >= projectSaveTime)
					{
						System.gc();
						project.write(ControlPanel.this);
						projectSaveTime = currentTime + Util.projectSaveInterval;
					}
				}

				if(isCancelled())
				{
					break;
				}
			}

			project.write(ControlPanel.this);
		}
		catch(Throwable ex)
		{
			showErrMsg(ex);
		}
	}
	
    private void cmdVerifyActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdVerifyActionPerformed
    {//GEN-HEADEREND:event_cmdVerifyActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Verifying pages...");
				verifyPages(true, false);
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdVerifyActionPerformed

    private void cmdRefactorActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdRefactorActionPerformed
    {//GEN-HEADEREND:event_cmdRefactorActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Refactoring project...");
				
				try
				{
					int totalPages = project.countAllPages();
					int currentPage = 0;
					
					initProgress(true, true, true);
					setProjectLimit(totalPages);
					
					long currentTime = System.currentTimeMillis();
					long projectSaveTime = currentTime + Util.projectSaveInterval;

					for(WikiPage page : project.listPages())
					{
						currentPage++;
						
						page.load(ControlPanel.this);
						
						if(page.hasZipName() && page.needsRefactoring())
						{
							/*
							 * Сначала исправляем картинки (если есть).
							 * Там только переименование, что не занимает много времени.
							 */
							
							for(WikiImage img : page.listImages())
							{
								if(img.hasFileName())
								{
									String oldFileName = img.getFileName();
									String newFileName = img.getDefaultFileName();
									
									if(!newFileName.equals(oldFileName))
									{
										File oldFile = new File(project.getImageDir(), oldFileName.replace('/', File.separatorChar));
										File newFile = new File(project.getImageDir(), newFileName.replace('/', File.separatorChar));
										
										if(!oldFile.isFile())
										{
											oldFile = new File(project.getImageArchiveDir(), oldFileName.replace('/', File.separatorChar));
											newFile = new File(project.getImageArchiveDir(), newFileName.replace('/', File.separatorChar));
										}
										
										newFile.getParentFile().mkdirs();
										
										if(oldFile.renameTo(newFile))
										{
											img.setFileName(newFileName);
										}
									}
								}
							}
							
							/*
							 * Марлезонский балет в двух частях.
							 * Сначала приводим ZIP к правильному виду.
							 */
							
							String oldZipName = page.getZipName();
							String newZipName = page.getDefaultZipName();
							
							File oldZip;
							File newZip;

							if(!newZipName.equals(oldZipName))
							{
								oldZip = new File(project.getWikiDir(), oldZipName.replace('/', File.separatorChar));
								newZip = new File(project.getWikiDir(), newZipName.replace('/', File.separatorChar));

								newZip.getParentFile().mkdirs();

								if(oldZip.renameTo(newZip))
								{
									page.setZipName(newZipName);
								}
							}
							
							/*
							 * А теперь приводим к правильному виду
							 * ревизии в уже правильном ZIP'е.
							 */

							if(page.needsRefactoring())
							{
								println("[" + currentPage + "/" + totalPages + "] " + page.getTitle());

								oldZip = new File(project.getWikiDir(), page.getZipName().replace('/', File.separatorChar) + ".tmp");
								newZip = new File(project.getWikiDir(), page.getZipName().replace('/', File.separatorChar));

								if(newZip.renameTo(oldZip))
								{
									page.refactor(oldZip, newZip, ControlPanel.this);

									if(newZip.isFile())
									{
										oldZip.delete();
									}
								}
							}
							
							page.unload(true);
						}
						else
						{
							page.unload(false);
						}
						
						setProjectProgress(currentPage);
						
						currentTime = System.currentTimeMillis();
						
						if(isCancelled())
						{
							break;
						}
						else if(currentTime >= projectSaveTime)
						{
							System.gc();
							project.write(ControlPanel.this);
							projectSaveTime = currentTime + Util.projectSaveInterval;
						}
					}

					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdRefactorActionPerformed

    private void cmdClearActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdClearActionPerformed
    {//GEN-HEADEREND:event_cmdClearActionPerformed
		synchronized(tfLog)
		{
			logBuffer = new StringBuilder(LOG_LIMIT);
			tfLog.setText(null);
		}
		
		updateProgress();
    }//GEN-LAST:event_cmdClearActionPerformed

    private void cmdRunGCActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdRunGCActionPerformed
    {//GEN-HEADEREND:event_cmdRunGCActionPerformed
		System.gc();
		updateProgress();
    }//GEN-LAST:event_cmdRunGCActionPerformed

    private void cmdPrintImageURLsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdPrintImageURLsActionPerformed
    {//GEN-HEADEREND:event_cmdPrintImageURLsActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Printing image URLs...");
				
				try
				{
					initProgress(true, false, true);
					setProjectLimit(project.countPages());
					
					OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "images.ini"), Util.BUFFER_SIZE);
					PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

					for(WikiNamespace ns : project.listNamespaces())
					{
						ArrayList<WikiPage> pages = project.listPages(ns.getID());

						if(isCancelled())
						{
							break;
						}
						else if(pages == null)
						{
							continue;
						}

						for(WikiPage page : pages)
						{
							page.load(ControlPanel.this);

							for(WikiImage img : page.listImages())
							{
								ps.println(img.getFullyQualifiedName() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + img.getURL());
								
								if(isCancelled())
								{
									break;
								}
							}

							page.unload(false);
							
							progressProject(1);
							
							if(isCancelled())
							{
								break;
							}
						}
					}

					ps.close();
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdPrintImageURLsActionPerformed

    private void cmdFixImageURLsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdFixImageURLsActionPerformed
    {//GEN-HEADEREND:event_cmdFixImageURLsActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Fixing image URLs...");
				
				try
				{
					initProgress(true, false, true);
					setProjectLimit(project.countPages());
					
					String target = tfImageURLFind.getText();
					String replacement = tfImageURLReplace.getText();

					for(WikiNamespace ns : project.listNamespaces())
					{
						ArrayList<WikiPage> pages = project.listPages(ns.getID());

						if(isCancelled())
						{
							break;
						}
						else if(pages == null)
						{
							continue;
						}

						for(WikiPage page : pages)
						{
							page.load(ControlPanel.this);

							for(WikiImage img : page.listImages())
							{
								img.setURL(img.getURL().replace(target, replacement));
							}

							page.unload(!page.listImages().isEmpty());
							
							progressProject(1);
							
							if(isCancelled())
							{
								break;
							}
						}
					}

					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdFixImageURLsActionPerformed

    private void cmdDownloadImagesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdDownloadImagesActionPerformed
    {//GEN-HEADEREND:event_cmdDownloadImagesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Downloading images...");
				
				try
				{
					initProgress(true, true, true);
					
					project.getImageDir().mkdirs();
					project.getImageArchiveDir().mkdirs();

					long currentTime = System.currentTimeMillis();
					long projectSaveTime = currentTime + Util.projectSaveInterval;

					for(WikiNamespace ns : project.listNamespaces())
					{
						if(isCancelled())
						{
							break;
						}
						
						println("Processing namespace " + ns.getID() + " (" + ns.getName() + ")...");

						int totalPages = project.countPages(ns.getID());
						int currentPage = 0;
						
						setProjectProgress(0);
						setProjectLimit(totalPages);

						ArrayList<WikiPage> pages = project.listPages(ns.getID());

						if(pages == null)
						{
							continue;
						}

						for(WikiPage page : pages)
						{
							currentPage++;
							
							page.load(ControlPanel.this);
							boolean updated = false;
							
							if(page.getDownloadStatus() != WikiPage.DOWNLOADED)
							{
								println("[" + currentPage + "/" + totalPages + "] " + page.getTitle());
								
								String newestTimestamp = page.getNewestImageTimestamp();

								setPageProgress(0);
								setPageLimit(page.listImages().size());

								for(WikiImage img : page.listImages())
								{
									if(!img.hasURL())
									{
										progressPage(1);
										continue;
									}

									String filename = img.getFileName();
									File archiveFile = new File(project.getImageArchiveDir(), filename);

									if(archiveFile.isFile() && !filename.equals(img.getDefaultFileName()))
									{
										filename = img.getDefaultFileName();
										archiveFile.renameTo(archiveFile = new File(project.getImageArchiveDir(), filename));
									}

									File file = new File(project.getImageDir(), filename);

									if(file.isFile() || archiveFile.isFile())
									{
										img.setFileName(filename);
										updated = true;
									}
									else
									{
										byte[] data = null;

										for(int tries = 0, maxtries = 5; tries < maxtries && !isCancelled(); tries++)
										{
											print("... revision " + img.getTimestamp());

											if(tries > 0)
											{
												print(" attempt " + (tries + 1));
											}

											try
											{
												data = Util.downloadFile(img.getURL(), ControlPanel.this);

												println(" - OK");
												break;
											}
											catch(FileNotFoundException ex)
											{
												img.setURL(null);
												
												println(" - Not Found");
												break;
											}
											catch(Throwable ex)
											{
												println(" - Error");
												showErrMsg(ex);

												if(tries < (maxtries - 1))
												{
													Thread.sleep(5000);
												}
											}
										}

										if(data != null)
										{
											try
											{
												FileOutputStream out = new FileOutputStream(file);
												out.write(data);
												out.close();

												img.setFileName(filename);
												updated = true;
											}
											catch(Throwable ex)
											{
												showErrMsg(ex);
											}
										}
									}

									if(!img.getTimestamp().equals(newestTimestamp) && file.isFile())
									{
										print("... moving file to archive");

										filename = img.getDefaultFileName();
										archiveFile = new File(project.getImageArchiveDir(), filename);
										img.setFileName(filename);

										if(file.renameTo(archiveFile))
										{
											println(" - OK");
										}
										else
										{
											println(" - failed");
										}
									}

									progressPage(1);

									currentTime = System.currentTimeMillis();

									if(isCancelled())
									{
										break;
									}
								}
							}
							
							page.unload(updated);
							
							progressProject(1);
							
							if(isCancelled())
							{
								break;
							}
							else if(currentTime >= projectSaveTime)
							{
								System.gc();
								project.write(ControlPanel.this);
								projectSaveTime = currentTime + Util.projectSaveInterval;
							}
						}
					}

					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdDownloadImagesActionPerformed

	protected OutputStream createDumpOutputStream() throws IOException
	{
		String filename = tfDumpFile.getText();
		
		if(filename == null || filename.isEmpty())
		{
			throw new IllegalArgumentException("empty file name");
		}
		
		OutputStream os;

		switch(cbDumpFormat.getSelectedIndex())
		{
			case 0:
				if(!filename.toLowerCase().endsWith(".xml"))
				{
					filename += ".xml";
				}
				
				os = new BufferedOutputStream(new FileOutputStream(filename), Util.BUFFER_SIZE);
				
				break;

			default:
				if(filename.toLowerCase().endsWith(".xml"))
				{
					filename += ".bz2";
				}
				else if(!filename.toLowerCase().endsWith(".xml.bz2"))
				{
					filename += ".xml.bz2";
				}
				
				os = new BufferedOutputStream(new FileOutputStream(filename), Util.BUFFER_SIZE);
				os = new BZip2CompressorOutputStream(os, BZip2CompressorOutputStream.MAX_BLOCKSIZE);
				
				break;

			case 2:
				os = new DummyOutputStream();
				break;
		}
		
		tfDumpFile.setText(filename);
		tfDumpFile.setCaretPosition(filename.length());
		
		return os;
	}
	
	protected InputStream createDumpInputStream() throws IOException
	{
		String filename = tfDumpFile.getText();
		
		if(filename == null || filename.isEmpty())
		{
			throw new IllegalArgumentException("empty file name");
		}
		
		InputStream is;

		switch(cbDumpFormat.getSelectedIndex())
		{
			case 0:
				if(!filename.toLowerCase().endsWith(".xml"))
				{
					filename += ".xml";
				}
				
				is = new BufferedInputStream(new FileInputStream(filename), Util.BUFFER_SIZE);
				
				break;

			default:
				if(filename.toLowerCase().endsWith(".xml"))
				{
					filename += ".bz2";
				}
				else if(!filename.toLowerCase().endsWith(".xml.bz2"))
				{
					filename += ".xml.bz2";
				}
				
				is = new BufferedInputStream(new FileInputStream(filename), Util.BUFFER_SIZE);
				is = new BZip2CompressorInputStream(is);
				
				break;

			case 2:
				throw new IllegalArgumentException("nothing to verify in dry run mode");
		}
		
		tfDumpFile.setText(filename);
		tfDumpFile.setCaretPosition(filename.length());
		
		return is;
	}
	
    private void cmdExportSinglePageActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdExportSinglePageActionPerformed
    {//GEN-HEADEREND:event_cmdExportSinglePageActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Exporting single page...");
				
				try
				{
					initProgress(true, true, false);
					
					WikiPage page;
					
					if(cbExportBy.getSelectedIndex() == 1)
					{
						page = project.getPage(Integer.parseInt(tfPageToExport.getText()));
					}
					else
					{
						page = project.getPage(tfPageToExport.getText());
					}
					
					if(page != null)
					{
						page.load(ControlPanel.this);
					}
					
					if(page != null && page.hasZipName())
					{
						println(page.getTitle());
						
						int inflateMode = cxAllRevisions.isSelected() ? WikiPage.INFLATE_ALL : WikiPage.INFLATE_LATEST;
						int uploadMode = cbDumpUploads.getSelectedIndex();

						CounterOutputStream counter = new CounterOutputStream(createDumpOutputStream());
						OutputStream os = new BufferedOutputStream(counter, Util.BUFFER_SIZE);

						XMLStringBuilder xml = new XMLStringBuilder(os, "UTF-8");
						xml.openTag("mediawiki");
						
						page.dump(xml, inflateMode, uploadMode, ControlPanel.this);
						
						xml.closeTag();
						xml.flush();

						xml.close();
						os.close();
						
						page.unload(false);
					}
					else
					{
						println("Nothing to export");
					}
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdExportSinglePageActionPerformed

    private void tfDumpFileMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_tfDumpFileMouseClicked
    {//GEN-HEADEREND:event_tfDumpFileMouseClicked
		if(evt.getClickCount() >= 2)
		{
			JFileChooser chooser = new JFileChooser(projectDir);
			chooser.showOpenDialog(this);
			
			if(chooser.getSelectedFile() != null)
			{
				tfDumpFile.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		}
    }//GEN-LAST:event_tfDumpFileMouseClicked

    private void cmdExportAllPagesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdExportAllPagesActionPerformed
    {//GEN-HEADEREND:event_cmdExportAllPagesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Exporting all pages...");
				
				try
				{
					initProgress(true, true, true);
					
					boolean dryrun = cbDumpFormat.getSelectedIndex() == 2;
					int inflateMode = cxAllRevisions.isSelected() ? WikiPage.INFLATE_NOT_UPLOADED : WikiPage.INFLATE_LATEST;
					int uploadMode = cbDumpUploads.getSelectedIndex();
					
					CounterOutputStream counter = new CounterOutputStream(createDumpOutputStream());
					OutputStream os = new BufferedOutputStream(counter, Util.BUFFER_SIZE);

					XMLStringBuilder xml = new XMLStringBuilder(os, "UTF-8");
					xml.openTag("mediawiki");

					int exportedPages = 0;
					int exportedRevisions = 0;
					int errorsOccurred = 0;

					int totalPages = project.countPages();
					int currentPage = 0;

					setProjectProgress(0);
					setProjectLimit(totalPages);
					
					boolean projectUpdated = false;
					boolean pageUpdated;

					for(WikiNamespace ns : project.listNamespaces())
					{
						ArrayList<WikiPage> pages = project.listPages(ns.getID());

						if(isCancelled())
						{
							break;
						}
						else if(pages == null)
						{
							continue;
						}

						for(WikiPage page : pages)
						{
							currentPage++;
							
							page.load(ControlPanel.this);
							pageUpdated = false;

							if(page.hasZipName() && (inflateMode == WikiPage.INFLATE_LATEST || !page.isUploaded()) && page.isContainedIn() < 0)
							{
								println("[" + currentPage + "/" + totalPages + "] " + page.getTitle());
								
								try
								{
									exportedRevisions += page.dump(xml, inflateMode, uploadMode, ControlPanel.this);
									xml.reset();

									if(!isCancelled())
									{
										if(inflateMode != WikiPage.INFLATE_LATEST)
										{
											page.setUploaded(true);
											pageUpdated = true;
										}
										
										exportedPages++;
									}
								}
								catch(Throwable ex)
								{
									showErrMsg(ex);
									errorsOccurred++;
								}
							}
							
							if(pageUpdated)
							{
								page.unload(true);
								projectUpdated = true;
							}
							else
							{
								page.unload(false);
							}

							setProjectProgress(currentPage);

							if(isCancelled())
							{
								break;
							}
						}
					}

					xml.closeTag();
					xml.flush();

					xml.close();
					os.close();

					println();
					println("Exported " + exportedRevisions + " revisions in " + exportedPages + " pages");
					println("Total of " + Util.formatNumber(counter.getCount()) + " bytes written, CRC32 is " + Long.toHexString(counter.getChecksum() & 0xFFFFFFFFL).toUpperCase());
					println("         " + errorsOccurred + " errors occurred in the process");
					println();

					if(!dryrun && projectUpdated)
					{
						System.gc();
						project.write(ControlPanel.this);
					}
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdExportAllPagesActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt)//GEN-FIRST:event_formWindowClosing
    {//GEN-HEADEREND:event_formWindowClosing
		closeLogStreams();
    }//GEN-LAST:event_formWindowClosing

    private void cmdSetUploadedFlagsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdSetUploadedFlagsActionPerformed
    {//GEN-HEADEREND:event_cmdSetUploadedFlagsActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Setting uploaded flags...");
				
				try
				{
					initProgress(true, false, true);
					setProjectLimit(project.countPages());
					
					boolean flag = cbUploadedFlags.getSelectedIndex() == 1;

					for(WikiNamespace ns : project.listNamespaces())
					{
						println("Processing namespace " + ns.getID() + " (" + ns.getName() + ")...");

						ArrayList<WikiPage> pages = project.listPages(ns.getID());

						if(pages == null)
						{
							continue;
						}

						for(WikiPage page : pages)
						{
							page.load(ControlPanel.this);
							page.setUploaded(flag);
							page.unload(true);
							
							progressProject(1);
						}
					}

					System.gc();
					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
		
		
    }//GEN-LAST:event_cmdSetUploadedFlagsActionPerformed

	protected void updatePageStatus()
	{
		WikiPage page = null;
		
		try
		{
			if(cbExportBy.getSelectedIndex() == 1)
			{
				page = project.getPage(Integer.parseInt(tfPageToExport.getText()));
				
				if(page != null)
				{
					tfPageToExport.setToolTipText(page.getTitle());
				}
			}
			else
			{
				page = project.getPage(tfPageToExport.getText());
				
				if(page != null)
				{
					tfPageToExport.setToolTipText(Integer.toString(page.getID()));
				}
			}
		}
		catch(Throwable ex)
		{
		}
		
		if(page != null)
		{
			try
			{
				page.load(ControlPanel.this);
			}
			catch(Throwable ex)
			{
				showErrMsg(ex);
			}
			
			switch(page.getDownloadStatus())
			{
				case WikiPage.REQUIRES_DOWNLOAD:
					lPageStatus.setText("-");
					lPageStatus.setToolTipText("requires download");
					break;
					
				case WikiPage.PARTIALLY_DOWNLOADED:
					lPageStatus.setText("#");
					lPageStatus.setToolTipText("partially downloaded");
					break;
					
				case WikiPage.DOWNLOADED:
					lPageStatus.setText("+");
					lPageStatus.setToolTipText("downloaded");
					break;
					
				default:
					lPageStatus.setText("*");
					lPageStatus.setToolTipText("status " + page.getDownloadStatus());
					break;
			}
		}
		else
		{
			tfPageToExport.setToolTipText(null);
			
			lPageStatus.setText("?");
			lPageStatus.setToolTipText("page not found");
		}
	}
	
    private void tfPageToExportFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tfPageToExportFocusLost
    {//GEN-HEADEREND:event_tfPageToExportFocusLost
		updatePageStatus();
    }//GEN-LAST:event_tfPageToExportFocusLost

    private void cbExportByActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cbExportByActionPerformed
    {//GEN-HEADEREND:event_cbExportByActionPerformed
		updatePageStatus();
    }//GEN-LAST:event_cbExportByActionPerformed

    private void cmdPrintUserListActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdPrintUserListActionPerformed
    {//GEN-HEADEREND:event_cmdPrintUserListActionPerformed
		try
		{
			prepareTask("Printing user list...");
			
			OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "users.ini"), Util.BUFFER_SIZE);
			PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

			for(WikiUser u : project.listUsers())
			{
				ps.println(u.getName() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + u.getPassword());
			}

			ps.close();
			os.close();
			
			finishTask();
		}
		catch(Throwable ex)
		{
			showErrMsg(ex);
		}
    }//GEN-LAST:event_cmdPrintUserListActionPerformed

    private void cmdPrintNamespaceListActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdPrintNamespaceListActionPerformed
    {//GEN-HEADEREND:event_cmdPrintNamespaceListActionPerformed
		try
		{
			prepareTask("Printing namespace list...");
			
			OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "namespaces.ini"), Util.BUFFER_SIZE);
			PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

			for(WikiNamespace ns : project.listAllNamespaces())
			{
				ps.println(ns.getID() + "\t=\t" + ns.getName());
			}

			ps.println();

			for(WikiNamespace ns : project.listAllNamespaces())
			{
				if(ns.getID() >= 100)
				{
					ps.println("$wgExtraNamespaces[" + ns.getID() + "] = \"" + ns.getName().replace(' ', '_') + "\";");
				}
			}

			ps.close();
			
			finishTask();
		}
		catch(Throwable ex)
		{
			showErrMsg(ex);
		}
    }//GEN-LAST:event_cmdPrintNamespaceListActionPerformed

    private void cmdPrintPageListActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdPrintPageListActionPerformed
    {//GEN-HEADEREND:event_cmdPrintPageListActionPerformed
		try
		{
			prepareTask("Printing page list...");
			
			OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "pages.ini"), Util.BUFFER_SIZE);
			PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

			for(WikiNamespace ns : project.listNamespaces())
			{
				ps.println(IniEntry.SECTION_START + ns.getID() + ":" + ns.getName() + IniEntry.SECTION_END);
				ps.println();

				ArrayList<WikiPage> pages = project.listPages(ns.getID());

				if(pages != null)
				{
					for(WikiPage p : pages)
					{
						ps.println(p.getTitle() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + p.getID());
					}

					ps.println();
				}
			}

			ps.close();
			
			finishTask();
		}
		catch(Throwable ex)
		{
			showErrMsg(ex);
		}
    }//GEN-LAST:event_cmdPrintPageListActionPerformed

    private void cmdCreateUsersActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdCreateUsersActionPerformed
    {//GEN-HEADEREND:event_cmdCreateUsersActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Creating users...");
				
				try
				{
					initProgress(true, false, true);
					
					MultiPartPost mpp = new MultiPartPost();

					int totalUsers = project.countUsers();
					int currentUser = 0;
					
					setProjectLimit(totalUsers);
					setOperationLimit(3);

					long currentTime = System.currentTimeMillis();
					long projectSaveTime = currentTime + Util.projectSaveInterval;
					
					String token = null;

					for(WikiUser user : project.listUsers())
					{
						setOperationProgress(0);
						currentUser++;
						
						if(!user.exists())
						{
							print("[" + currentUser + "/" + totalUsers + "] " + user.getName());
							
							if(token == null)
							{
								mpp.addParam("format", "xml");
								mpp.addParam("uselang", "en");
								mpp.addParam("action", "query");
								mpp.addParam("meta", "tokens");
								mpp.addParam("type", "createaccount");
								
								String response = mpp.post(project.getTargetURL() + "api.php");
								token = Util.substring(response, "createaccounttoken=\"", "\"");
							}
							
							progressOperation(1);

							mpp.addParam("format", "xml");
							mpp.addParam("uselang", "en");
							mpp.addParam("action", "createaccount");
							mpp.addParam("username", user.getName());
							mpp.addParam("password", user.getPassword());
							mpp.addParam("retype", user.getPassword());
							mpp.addParam("createreturnurl", project.getTargetURL());
							mpp.addParam("createtoken", token);

							String response = mpp.post(project.getTargetURL() + "api.php");
							String status = Util.substring(response, "status=\"", "\"");
							
							progressOperation(1);

							if(response.contains("already in use") || response.contains("userexists"))
							{
								println(" - exists");
								user.setExists(true);
							}
							else if(response.contains("not specified a valid username") || response.contains("invaliduser"))
							{
								println(" - invalid");
							}
							else if("PASS".equalsIgnoreCase(status))
							{
								println(" - OK");
								user.setExists(true);
							}
							else
							{
								println(" - failed");
								throw new IOException(response);
							}
							
							progressOperation(1);

							currentTime = System.currentTimeMillis();

							if(currentTime >= projectSaveTime)
							{
								System.gc();
								project.write(ControlPanel.this);
								projectSaveTime = currentTime + Util.projectSaveInterval;
							}
						}
						
						setProjectProgress(currentUser);
						
						if(isCancelled())
						{
							break;
						}
					}

					System.gc();
					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdCreateUsersActionPerformed

    private void cmdSetUserExistFlagsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdSetUserExistFlagsActionPerformed
    {//GEN-HEADEREND:event_cmdSetUserExistFlagsActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Setting user exist flags...");
				
				try
				{
					boolean exists = cbUserExistFlags.getSelectedIndex() == 1;

					for(WikiUser user : project.listUsers())
					{
						user.setExists(exists);
					}

					System.gc();
					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdSetUserExistFlagsActionPerformed

    private void cmdImportViaScriptActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdImportViaScriptActionPerformed
    {//GEN-HEADEREND:event_cmdImportViaScriptActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Running import script...");
				
				try
				{
					initProgress(true, true, true);
					
					boolean dryrun = cbDumpFormat.getSelectedIndex() == 2;
					int inflateMode = cxAllRevisions.isSelected() ? WikiPage.INFLATE_NOT_UPLOADED : WikiPage.INFLATE_LATEST;
					int uploadMode = cbDumpUploads.getSelectedIndex();
					
					File scriptFile = new File((project.getLocalSiteRoot() + "maintenance/importDump.php").replace('/', File.separatorChar));
					
					if(!scriptFile.isFile())
					{
						throw new IllegalArgumentException("import script does not exist");
					}
					
					String imageBasePath = project.getImageBaseDir().getAbsolutePath();
					
					int exportedPages = 0;
					int exportedRevisions = 0;
					long bytesWritten = 0;
					int errorsOccurred = 0;

					int totalPages = project.countPages();
					int currentPage = 0;

					setProjectProgress(0);
					setProjectLimit(totalPages);
					
					boolean projectUpdated = false;
					boolean pageUpdated;

					for(WikiNamespace ns : project.listNamespaces())
					{
						ArrayList<WikiPage> pages = project.listPages(ns.getID());

						if(isCancelled())
						{
							break;
						}
						else if(pages == null)
						{
							continue;
						}

						for(WikiPage page : pages)
						{
							currentPage++;
							
							page.load(ControlPanel.this);
							pageUpdated = false;

							if(page.hasZipName() && (inflateMode == WikiPage.INFLATE_LATEST || !page.isUploaded()) && page.isContainedIn() < 0)
							{
								println("[" + currentPage + "/" + totalPages + "] " + page.getTitle());
								
								try
								{
									ArrayList<String> command = new ArrayList(10);
									
									command.add("php");
									command.add(scriptFile.getAbsolutePath());
									// command.add("--quiet");
									
									if(dryrun)
									{
										command.add("--dry-run");
									}
									
									command.add("--uploads");
									command.add("--image-base-path");
									command.add(imageBasePath);
									
									System.out.print("Executing command:");
									
									for(String arg : command)
									{
										if(arg.contains(" "))
										{
											System.out.print(" \"" + arg + "\"");
										}
										else
										{
											System.out.print(" " + arg);
										}
									}
									
									System.out.println();
									
									ProcessBuilder builder = new ProcessBuilder(command);
									
									builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
									builder.redirectError(ProcessBuilder.Redirect.INHERIT);
									
									script = builder.start();
									
									CounterOutputStream counter = new CounterOutputStream(script.getOutputStream());
									OutputStream os = new BufferedOutputStream(counter, Util.BUFFER_SIZE);
									
									XMLStringBuilder xml = new XMLStringBuilder(os, "UTF-8");
									xml.openTag("mediawiki");
									
									exportedRevisions += page.dump(xml, inflateMode, uploadMode, ControlPanel.this);
									
									xml.closeTag();
									xml.flush();
									
									bytesWritten += counter.getCount();
									
									xml.close();
									os.close();
									
									System.out.println("Waiting for process to exit...");

									int result = script.waitFor();
									
									System.out.println();
									
									script = null;
									
									if(result == 0)
									{
										if(!isCancelled())
										{
											if(inflateMode != WikiPage.INFLATE_LATEST && !dryrun)
											{
												page.setUploaded(true);
												pageUpdated = true;
											}

											exportedPages++;
										}
									}
									else
									{
										throw new RuntimeException("import script exited with code " + result);
									}
								}
								catch(Throwable ex)
								{
									showErrMsg(ex);
									errorsOccurred++;
								}
							}
							
							if(pageUpdated)
							{
								page.unload(true);
								projectUpdated = true;
							}
							else
							{
								page.unload(false);
							}

							setProjectProgress(currentPage);

							if(isCancelled())
							{
								break;
							}
						}
					}

					println();
					println("Imported " + exportedRevisions + " revisions in " + exportedPages + " pages");
					println("Total of " + Util.formatNumber(bytesWritten) + " bytes written");
					println("         " + errorsOccurred + " errors occurred in the process");
					println();

					if(!dryrun && projectUpdated)
					{
						System.gc();
						project.write(ControlPanel.this);
					}
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdImportViaScriptActionPerformed

    private void tfLocalSiteRootFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tfLocalSiteRootFocusGained
    {//GEN-HEADEREND:event_tfLocalSiteRootFocusGained
		tfLocalSiteRoot.selectAll();
    }//GEN-LAST:event_tfLocalSiteRootFocusGained

    private void tfLocalSiteRootFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tfLocalSiteRootFocusLost
    {//GEN-HEADEREND:event_tfLocalSiteRootFocusLost
		if(project != null)
		{
			String prevPath = project.getLocalSiteRoot();
			
			project.setLocalSiteRoot(tfLocalSiteRoot.getText());
			tfLocalSiteRoot.setText(project.getLocalSiteRoot());
			
			if(prevPath == null || !prevPath.equals(project.getLocalSiteRoot()))
			{
				Runnable task = new Runnable()
				{
					public void run()
					{
						prepareTask(null);

						try
						{
							project.write(ControlPanel.this);
						}
						catch(Throwable ex)
						{
							showErrMsg(ex);
						}

						finishTask();
					}
				};

				(new Thread(task)).start();
			}
		}
    }//GEN-LAST:event_tfLocalSiteRootFocusLost

    private void cmdVerifyUploadsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdVerifyUploadsActionPerformed
    {//GEN-HEADEREND:event_cmdVerifyUploadsActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Verifying uploads...");
				
				try
				{
					ImageUploadVerifier.verifyImages(project, ControlPanel.this, Util.pagesPerRequest, Util.projectSaveInterval);
					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdVerifyUploadsActionPerformed

    private void cmdVerifyImagesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdVerifyImagesActionPerformed
    {//GEN-HEADEREND:event_cmdVerifyImagesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Verifying images...");
				verifyPages(false, true);
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdVerifyImagesActionPerformed

    private void cmdPrintReportActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdPrintReportActionPerformed
    {//GEN-HEADEREND:event_cmdPrintReportActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Generating report...");
				
				try
				{
					int totalPages = project.countAllPages();
					int currentPage = 0;
					
					initProgress(true, false, true);
					setProjectLimit(totalPages);
					
					int[] pageCountByDLStatus = new int[3];
					
					int missingPages = 0;
					int actualPages = 0;
					
					int totalRevisions = 0;
					int downloadedRevisions = 0;
					
					int pagesWithImages = 0;
					
					int totalImages = 0;
					int downloadedImages = 0;
					
					HashMap<Integer, int[]> namespaceCounters = new HashMap();
					HashMap<Integer, String[]> namespaceSummary = new HashMap();
					
					for(WikiNamespace ns : project.listAllNamespaces())
					{
						namespaceCounters.put(ns.getID(), new int[3]);
						
						String[] summary = new String[5];
						
						summary[0] = Integer.toString(ns.getID());
						summary[1] = ns.getName();
						
						namespaceSummary.put(ns.getID(), summary);
					}

					for(WikiPage page : project.listPages())
					{
						page.load(ControlPanel.this);
						
						pageCountByDLStatus[page.getDownloadStatus()]++;
						
						if(page.isMissing())
						{
							missingPages++;
						}
						
						if(page.isActual())
						{
							actualPages++;
						}
						
						totalRevisions += page.countRevisions();
						downloadedRevisions += page.countDownloadedRevisions();
						
						if(page.hasImages())
						{
							pagesWithImages++;
						}
						
						totalImages += page.countImages();
						downloadedImages += page.countDownloadedImages();
						
						int[] counters = namespaceCounters.get(page.getNS());
						
						if(page.isUploaded())
						{
							counters[0]++;
						}
						
						if(page.getDownloadStatus() == WikiPage.DOWNLOADED)
						{
							counters[1]++;
						}
						
						counters[2]++;
						
						page.unload(false);
						
						setProjectProgress(++currentPage);
						
						if(isCancelled())
						{
							break;
						}
					}

					if(!isCancelled())
					{
						println();

						println("Selected " + project.countNamespaces() + " of " + project.countAllNamespaces() + " namespaces");
						println();

						println("Total pages:  " + totalPages);

						if(actualPages == totalPages)
						{	
							println("Actual pages: " + actualPages);
						}
						else
						{
							println("Actual pages: " + actualPages + " (" + (totalPages - actualPages) + " less)");
						}

						println("Missing: " + missingPages);
						println();

						println("Pages not downloaded:       " + pageCountByDLStatus[WikiPage.REQUIRES_DOWNLOAD]);
						println("Pages partially downloaded: " + pageCountByDLStatus[WikiPage.PARTIALLY_DOWNLOADED]);
						println("Pages fully downloaded:     " + pageCountByDLStatus[WikiPage.DOWNLOADED]);
						println();

						println("Total revisions:      " + totalRevisions);

						if(downloadedRevisions == totalRevisions)
						{
							println("Downloaded revisions: " + downloadedRevisions);
						}
						else
						{
							println("Downloaded revisions: " + downloadedRevisions + " (" + (totalRevisions - downloadedRevisions) + " less)");
						}

						println();

						println("Pages with images: " + pagesWithImages);
						println();

						println("Total images:      " + totalImages);

						if(downloadedImages == totalImages)
						{
							println("Downloaded images: " + downloadedImages);
						}
						else
						{
							println("Downloaded images: " + downloadedImages + " (" + (totalImages - downloadedImages) + " less)");
						}

						println();

						println("Pages by namespace: (uploaded | downloaded | total)");
						println();

						for(WikiNamespace ns : project.listAllNamespaces())
						{
							int[] counters = namespaceCounters.get(ns.getID());
							String[] line = namespaceSummary.get(ns.getID());

							for(int i = 0; i < 3; i++)
							{
								line[i + 2] = Integer.toString(counters[i]);
							}
						}

						for(WikiNamespace ns : project.listAllNamespaces())
						{
							String[] line = namespaceSummary.get(ns.getID());

							for(String[] otherLine : namespaceSummary.values())
							{
								for(int i = 0; i < 5; i++)
								{
									while(line[i].length() < otherLine[i].length())
									{
										line[i] = " " + line[i];
									}
								}
							}

							println(line[0] + ": " + line[1] + " ( " + line[2] + " | " + line[3] + " | " + line[4] + " )");
						}

						println();
					}
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdPrintReportActionPerformed

    private void cmdListRenamesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdListRenamesActionPerformed
    {//GEN-HEADEREND:event_cmdListRenamesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Requesting rename list...");
				
				try
				{
					RenameListParser.getRenameList(project, ControlPanel.this);
					
					project.write(ControlPanel.this);
					
					println("Printing...");

					OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "renames.ini"), Util.BUFFER_SIZE);
					PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
					
					for(WikiRename rename : project.listRenames())
					{
						ps.println("[" + rename.getTimeStamp() + "]\t" + rename.getSourceTitle() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + rename.getDestTitle());
					}

					ps.close();
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdListRenamesActionPerformed

    private void cmdPrintRenameListActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdPrintRenameListActionPerformed
    {//GEN-HEADEREND:event_cmdPrintRenameListActionPerformed
		try
		{
			prepareTask("Printing rename list...");
			
			OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "renames.ini"), Util.BUFFER_SIZE);
			PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

			for(WikiRename rename : project.listRenames())
			{
				ps.println("[" + rename.getTimeStamp() + "]\t" + rename.getSourceTitle() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + rename.getDestTitle());
			}

			ps.close();
			
			finishTask();
		}
		catch(Throwable ex)
		{
			showErrMsg(ex);
		}
    }//GEN-LAST:event_cmdPrintRenameListActionPerformed

    private void cmdGuessImageURLsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdGuessImageURLsActionPerformed
    {//GEN-HEADEREND:event_cmdGuessImageURLsActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Guessing image URLs...");
				
				try
				{
					initProgress(true, false, true);
					setProjectLimit(project.countPages());
					
					for(WikiPage page : project.listPages())
					{
						if(page.supposedToHaveImages())
						{
							page.load(ControlPanel.this);
							page.unload(page.guessImageURLs());
						}
						
						progressProject(1);

						if(isCancelled())
						{
							break;
						}
					}
					
					project.write(ControlPanel.this);
					
					println("Printing list...");
					
					initProgress(true, false, true);
					setProjectLimit(project.countPages());
					
					OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "images.ini"), Util.BUFFER_SIZE);
					PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

					for(WikiNamespace ns : project.listNamespaces())
					{
						ArrayList<WikiPage> pages = project.listPages(ns.getID());

						if(isCancelled())
						{
							break;
						}
						else if(pages == null)
						{
							continue;
						}

						for(WikiPage page : pages)
						{
							page.load(ControlPanel.this);

							for(WikiImage img : page.listImages())
							{
								ps.println(img.getFullyQualifiedName() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + img.getURL());
								
								if(isCancelled())
								{
									break;
								}
							}

							page.unload(false);
							
							progressProject(1);
							
							if(isCancelled())
							{
								break;
							}
						}
					}

					ps.close();
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdGuessImageURLsActionPerformed

    private void cmdGetPagesWithImageURLsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdGetPagesWithImageURLsActionPerformed
    {//GEN-HEADEREND:event_cmdGetPagesWithImageURLsActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Downloading pages and image URLs...");
				
				try
				{
					PageContentParser.getPagesContents(project, ControlPanel.this, Util.pagesPerRequest, Util.projectSaveInterval, true);
					project.write(ControlPanel.this);
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdGetPagesWithImageURLsActionPerformed

    private void cmdRenamePagesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdRenamePagesActionPerformed
    {//GEN-HEADEREND:event_cmdRenamePagesActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_cmdRenamePagesActionPerformed

    private void cmdFindDuplicatePagesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdFindDuplicatePagesActionPerformed
    {//GEN-HEADEREND:event_cmdFindDuplicatePagesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Searching duplicate revisions...");
				
				try
				{
					int totalPages = project.countAllPages();
					int currentPage = 0;
					
					initProgress(true, false, true);
					setProjectLimit(totalPages);
					
					HashMap<Integer, ArrayList<WikiPage>> index = new HashMap();

					for(WikiPage page : project.listPages())
					{
						page.load(ControlPanel.this);
						
						for(WikiRevision rv : page.listRevisions())
						{
							ArrayList<WikiPage> list = index.get(rv.getID());
							
							if(list == null)
							{
								list = new ArrayList();
								index.put(rv.getID(), list);
							}
							
							list.add(page);
						}
						
						page.unload(false);
						
						setProjectProgress(++currentPage);
						
						if(isCancelled())
						{
							break;
						}
					}

					print("Building duplication clusters...");
					
					initProgress(false, false, true);
					setProjectLimit(index.size());
					setProjectProgress(currentPage = 0);
					
					ArrayList<ArrayList<WikiPage>> clusters = new ArrayList();
					
					Comparator<WikiPage> sortByID = new Comparator<WikiPage>()
					{
						public int compare(WikiPage a, WikiPage b)
						{
							return a.getID() - b.getID();
						}
					};
					
					for(ArrayList<WikiPage> list : index.values())
					{
						if(list.size() > 1)
						{
							Collections.sort(list, sortByID);

							if(!clusters.contains(list))
							{
								clusters.add(list);
							}
						}
						
						setProjectProgress(++currentPage);
						
						if(isCancelled())
						{
							break;
						}
					}
					
					println(" " + clusters.size() + " found");
					
					index.clear();
					
					System.gc();
					updateProgress();
					
					println("Testing pages...");
					
					initProgress(true, true, true);
					setProjectLimit(clusters.size());
					setProjectProgress(currentPage = 0);
					
					for(ArrayList<WikiPage> list : clusters)
					{
						for(WikiPage page : list)
						{
							page.load(ControlPanel.this);
						}
						
						for(int i = 0; i < list.size() && !isCancelled(); i++)
						{
							for(int j = i + 1; j < list.size() && !isCancelled(); j++)
							{
								WikiPage pageA = list.get(i);
								WikiPage pageB = list.get(j);
								
								boolean AinB = pageA.isContainedIn(pageB, ControlPanel.this);
								boolean BinA = pageB.isContainedIn(pageA, ControlPanel.this);
								
								if(AinB && BinA)
								{
									// Страницы содержатся друг в друге, то есть это точные копии.
									// Отдаем предпочтение той, у которой меньше ID пространства имен.
									// Если они в одном пространстве, то выбираем более новую.
									
									if(pageA.getNS() < pageB.getNS())
									{
										pageB.setContainedIn(pageA.getID());
									}
									else if(pageB.getNS() < pageA.getNS())
									{
										pageA.setContainedIn(pageB.getID());
									}
									else if(pageA.getID() > pageB.getID())
									{
										pageB.setContainedIn(pageA.getID());
									}
									else if(pageB.getID() > pageA.getID())
									{
										pageA.setContainedIn(pageB.getID());
									}
									else
									{
										// Такого не должно быть.
										// Если так есть, это надо фиксить через рефакторинг проекта.
										
										println("Internal duplicate: " + pageA.getTitle() + " <-> " + pageB.getTitle());
									}
								}
								else if(AinB)
								{
									pageA.setContainedIn(pageB.getID());
								}
								else if(BinA)
								{
									pageB.setContainedIn(pageA.getID());
								}
							}
						}
						
						for(WikiPage page : list)
						{
							page.unload(page.isContainedIn() >= 0);
						}
						
						setProjectProgress(++currentPage);
						
						if(isCancelled())
						{
							break;
						}
					}
					
					println("Normalizing inclusions...");
					
					initProgress(false, false, true);
					setProjectLimit(totalPages);
					setProjectProgress(currentPage = 0);
					
					ArrayList<WikiPage> branch = new ArrayList();
					
					for(WikiPage page : project.listPages())
					{
						while(true)
						{
							int containerID = page.isContainedIn();

							if(containerID >= 0)
							{
								branch.add(page);
								page = project.getPage(containerID);
							}
							else
							{
								break;
							}
						}
						
						for(WikiPage containedPage : branch)
						{
							containedPage.setContainedIn(page.getID());
						}
						
						branch.clear();
						
						setProjectProgress(++currentPage);
						
						if(isCancelled())
						{
							break;
						}
					}
					
					println("Building inclusion index...");
					
					initProgress(false, false, true);
					setProjectLimit(totalPages);
					setProjectProgress(currentPage = 0);
					
					for(WikiPage page : project.listPages())
					{
						int containerID = page.isContainedIn();

						if(containerID >= 0)
						{
							ArrayList<WikiPage> list = index.get(containerID);
							
							if(list == null)
							{
								list = new ArrayList();
								index.put(containerID, list);
							}
							
							list.add(page);
						}
						
						setProjectProgress(++currentPage);
						
						if(isCancelled())
						{
							break;
						}
					}
					
					if(!isCancelled())
					{
						project.write(ControlPanel.this);
					}
					
					println("Printing list...");
					
					OutputStream os = new BufferedOutputStream(new FileOutputStream(project.getBaseDir() + "duplicates.ini"), Util.BUFFER_SIZE);
					PrintWriter ps = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
					
					for(WikiNamespace ns : project.listNamespaces())
					{
						ArrayList<WikiPage> pagesByNS = project.listPages(ns.getID());

						if(pagesByNS != null)
						{
							for(WikiPage container : pagesByNS)
							{
								ArrayList<WikiPage> list = index.get(container.getID());
								
								if(list != null)
								{
									Collections.sort(list, sortByID);
									
									ps.println(IniEntry.SECTION_START + container.getTitle() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + container.getID() + IniEntry.SECTION_END);
									ps.println();

									for(WikiPage page : list)
									{
										ps.println(page.getTitle() + "\t" + IniEntry.VALUE_DELIMITER + "\t" + page.getID());
									}

									ps.println();
								}
							}
						}
					}
					
					ps.close();
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdFindDuplicatePagesActionPerformed

    private void cmdRemoveDuplicatePagesActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cmdRemoveDuplicatePagesActionPerformed
    {//GEN-HEADEREND:event_cmdRemoveDuplicatePagesActionPerformed
		Runnable task = new Runnable()
		{
			public void run()
			{
				prepareTask("Listing duplicate pages...");
				
				try
				{
					int totalPages = project.countAllPages();
					int currentPage = 0;
					
					initProgress(true, false, true);
					setProjectLimit(totalPages);
					
					ArrayList<WikiPage> duplicates = new ArrayList();

					for(WikiPage page : project.listPages())
					{
						page.load(ControlPanel.this);
						
						if(page.isContainedIn() >= 0)
						{
							duplicates.add(page);
						}
						
						page.unload(false);
						
						setProjectProgress(++currentPage);
						
						if(isCancelled())
						{
							break;
						}
					}

					if(!duplicates.isEmpty())
					{
						if(!isCancelled())
						{
							int timeout = 10;

							println("After " + timeout + " seconds " + duplicates.size() + " pages will be deleted!");

							while(timeout > 0 && !isCancelled())
							{
								print(Integer.toString(timeout--) + "... ");
								Thread.sleep(1000);
							}

							println();
						}

						if(!isCancelled())
						{
							println("Deletion commencing...");

							for(WikiPage page : duplicates)
							{
								File zip = new File(project.getWikiDir(), page.getZipName().replace('/', File.separatorChar));

								if(zip.isFile())
								{
									zip.delete();
								}

								project.removePage(page.getID());
							}

							project.write(ControlPanel.this);
						}
					}
					else
					{
						println("Nothing to delete");
					}
				}
				catch(Throwable ex)
				{
					showErrMsg(ex);
				}
				
				finishTask();
			}
		};
		
		(new Thread(task)).start();
    }//GEN-LAST:event_cmdRemoveDuplicatePagesActionPerformed

	/**
	 * @param args the command line arguments
	 */
	public static void main(String args[])
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch(Throwable ex)
		{
		}

		/*
		 * Create and display the form
		 */
		java.awt.EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				new ControlPanel().setVisible(true);
			}
		});
	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox cbDumpFormat;
    private javax.swing.JComboBox cbDumpUploads;
    private javax.swing.JComboBox cbExportBy;
    private javax.swing.JComboBox cbUploadedFlags;
    private javax.swing.JComboBox cbUserExistFlags;
    private javax.swing.JButton cmdCancel;
    private javax.swing.JButton cmdClear;
    private javax.swing.JButton cmdCreateUsers;
    private javax.swing.JButton cmdDownloadImages;
    private javax.swing.JButton cmdExportAllPages;
    private javax.swing.JButton cmdExportSinglePage;
    private javax.swing.JButton cmdFindDuplicatePages;
    private javax.swing.JButton cmdFixImageURLs;
    private javax.swing.JButton cmdFixNamespaces;
    private javax.swing.JButton cmdGetPages;
    private javax.swing.JButton cmdGetPagesWithImageURLs;
    private javax.swing.JButton cmdGuessImageURLs;
    private javax.swing.JButton cmdImportViaScript;
    private javax.swing.JButton cmdListNamespaces;
    private javax.swing.JButton cmdListPages;
    private javax.swing.JButton cmdListRenames;
    private javax.swing.JButton cmdListUsers;
    private javax.swing.JButton cmdOpen;
    private javax.swing.JButton cmdPrepareUpdate;
    private javax.swing.JButton cmdPrintImageURLs;
    private javax.swing.JButton cmdPrintNamespaceList;
    private javax.swing.JButton cmdPrintPageList;
    private javax.swing.JButton cmdPrintRenameList;
    private javax.swing.JButton cmdPrintReport;
    private javax.swing.JButton cmdPrintUserList;
    private javax.swing.JButton cmdRefactor;
    private javax.swing.JButton cmdRemoveDuplicatePages;
    private javax.swing.JButton cmdRenamePages;
    private javax.swing.JButton cmdRunGC;
    private javax.swing.JButton cmdSelectNamespaces;
    private javax.swing.JButton cmdSetUploadedFlags;
    private javax.swing.JButton cmdSetUserExistFlags;
    private javax.swing.JButton cmdVerify;
    private javax.swing.JButton cmdVerifyImages;
    private javax.swing.JButton cmdVerifyUploads;
    private javax.swing.JCheckBox cxAllRevisions;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel lHeap;
    private javax.swing.JLabel lOperation;
    private javax.swing.JLabel lPage;
    private javax.swing.JLabel lPageStatus;
    private javax.swing.JLabel lProject;
    private javax.swing.JList namespacesList;
    private javax.swing.JPanel pDump;
    private javax.swing.JPanel pImages;
    private javax.swing.JPanel pMeta;
    private javax.swing.JPanel pNamespaces;
    private javax.swing.JPanel pPages;
    private javax.swing.JPanel pProgress;
    private javax.swing.JPanel pUsers;
    private javax.swing.JProgressBar pbHeap;
    private javax.swing.JProgressBar pbOperation;
    private javax.swing.JProgressBar pbPage;
    private javax.swing.JProgressBar pbProject;
    private javax.swing.JPanel projectPanel;
    private javax.swing.JTabbedPane tabPane;
    private javax.swing.JTextField tfDumpFile;
    private javax.swing.JTextField tfImageURLFind;
    private javax.swing.JTextField tfImageURLReplace;
    private javax.swing.JTextField tfLocalSiteRoot;
    private final javax.swing.JTextArea tfLog = new javax.swing.JTextArea();
    private javax.swing.JTextField tfPageToExport;
    private javax.swing.JTextField tfSourceURL;
    private javax.swing.JTextField tfTargetURL;
    // End of variables declaration//GEN-END:variables
}
