/*******************************************************************************
 * Copyright (c) 2010 Alena Laskavaia 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia  - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.codan.internal.ui.views;

import java.util.Collection;

import org.eclipse.cdt.codan.internal.ui.CodanUIActivator;
import org.eclipse.cdt.codan.ui.AbstractCodanProblemDetailsProvider;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.internal.ui.util.EditorUtility;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Problems Details view show details for selected problem marker.
 * Other plugins can contribute to override default behaviour using 
 * codanProblemDetails extension point.
 */
public class ProblemDetails extends ViewPart {
	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "org.eclipse.cdt.codan.internal.ui.views.ProblemDetails"; //$NON-NLS-1$
	private Composite area;
	/**
	 * Control for problem message, which can include location
	 */
	private Link message;
	/**
	 * Control for problem description which can include links to help or web-sites with extra info
	 */
	private Link description;
	private GenericCodanProblemDetailsProvider genProvider = new GenericCodanProblemDetailsProvider();
	private AbstractCodanProblemDetailsProvider curProvider = genProvider;

	/**
	 * The constructor.
	 */
	public ProblemDetails() {
	}

	/**
	 * This is a callback that will allow us
	 * to create the area and initialize it.
	 */
	public void createPartControl(Composite parent) {
		final String problemsViewId = "org.eclipse.ui.views.ProblemView"; //$NON-NLS-1$
		area = new Composite(parent, SWT.NONE);
		area.setLayout(new GridLayout());
		SelectionAdapter linkSelAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String link = e.text;
				if (link == null)
					return;
				if (link.startsWith("http")) { //$NON-NLS-1$
					org.eclipse.swt.program.Program.launch(e.text);
					return;
				}
				// link file format example "file:/tmp/file.c#42", 42 is the line number
				if (link.startsWith("file:")) { //$NON-NLS-1$
					openFile(link);
					return;
				}
				if (link.startsWith("help:")) { //$NON-NLS-1$
					// open in eclipse help TODO
					return;
				}
			}
		};
		message = new Link(area, SWT.WRAP);
		message.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		message.addSelectionListener(linkSelAdapter);
		description = new Link(area, SWT.WRAP);
		description.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		description.addSelectionListener(linkSelAdapter);
		ISelectionService ser = (ISelectionService) getSite().getService(ISelectionService.class);
		ser.addSelectionListener(new ISelectionListener() {
			public void selectionChanged(IWorkbenchPart part, ISelection selection) {
				if (part.getSite().getId().equals(problemsViewId)) {
					processSelection(selection);
				}
			}
		});
		ISelection selection = ser.getSelection(problemsViewId);
		processSelection(selection);
	}

	protected void processSelection(ISelection selection) {
		if (selection == null || selection.isEmpty())
			return;
		if (selection instanceof IStructuredSelection) {
			Object firstElement = ((IStructuredSelection) selection).getFirstElement();
			IMarker marker = null;
			if (firstElement instanceof IAdaptable) {
				marker = (IMarker) ((IAdaptable) firstElement).getAdapter(IMarker.class);
			} else if (firstElement instanceof IMarker) {
				marker = (IMarker) firstElement;
			}
			if (marker != null) {
				queryProviders(marker);
				area.layout();
			}
		}
	}

	private void queryProviders(IMarker marker) {
		String id = marker.getAttribute(IMarker.PROBLEM, "id"); //$NON-NLS-1$
		Collection<AbstractCodanProblemDetailsProvider> providers = ProblemDetailsExtensions.getProviders(id);
		for (AbstractCodanProblemDetailsProvider provider : providers) {
			synchronized (provider) {
				provider.setMarker(marker);
				if (provider.isApplicable(id)) {
					applyProvider(provider);
					return;
				}
			}
		}
		genProvider.setMarker(marker);
		applyProvider(genProvider);
	}

	private void applyProvider(AbstractCodanProblemDetailsProvider provider) {
		curProvider = provider;
		setTextSafe(message, provider, provider.getStyledProblemMessage());
		setTextSafe(description, provider, provider.getStyledProblemDescription());
	}

	protected void setTextSafe(Link control, AbstractCodanProblemDetailsProvider provider, String text) {
		try {
			control.setText(text);
		} catch (Exception e) {
			// this is debug message
			control.setText("failed to set text: " + provider.getClass() + " " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/**
	 * Passing the focus request to the area's control.
	 */
	public void setFocus() {
		message.setFocus();
	}

    @SuppressWarnings("restriction")
    public void openFile(String link) {
	    String file = link.replaceFirst("^file:", ""); //$NON-NLS-1$ //$NON-NLS-2$
	    file = file.replaceAll("#\\d+$", ""); //$NON-NLS-1$ //$NON-NLS-2$
	    String sline = link.replaceAll(".*#(\\d+)$", "$1"); //$NON-NLS-1$ //$NON-NLS-2$
	    try {
	    	IPath pfile = new Path(file);
	    	IResource markerResource = curProvider.getMarker().getResource();
			ICElement element = CoreModel.getDefault().create(markerResource);
	    	IEditorPart part = EditorUtility.openInEditor(pfile, element);
	    	int line = 0;
	    	try {
	    		line = Integer.parseInt(sline);
	    	} catch (NumberFormatException e2) {
	    		// no line
	    	}
	    	if (line > 0) {
	    		if (part instanceof ITextEditor) {
	    			ITextEditor textEditor = (ITextEditor) part;
					IDocument document = textEditor.getDocumentProvider().getDocument(
	    			        part.getEditorInput());
	    			try {
	                    textEditor.selectAndReveal(document.getLineOffset(line-1), 0);
	                } catch (BadLocationException e1) {
	                    return;
	                }
	    		}
	    	}
	    } catch (PartInitException e1) {
	    	CodanUIActivator.log(e1);
	    }
    }
}