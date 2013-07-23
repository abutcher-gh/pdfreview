package com.jessamine.pdfreview.confluence;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.atlassian.confluence.core.ContentPropertyManager;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.setup.BootstrapManager;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.user.User;

public class ManageReviewLogs extends ConfluenceActionSupport {
	private static final long serialVersionUID = 9063317947114043994L;
	private BootstrapManager bootstrapManager;
	private PageManager pageManager;
	private SpaceManager spaceManager;
	private ContentPropertyManager contentPropertyManager;

	private String spaceKey;
	private String reviewPath;
	private String reviewLabel;

	class PageTree {
		PageTree() {
			this.root = true;
			id = 0;
		}

		PageTree(long id) {
			this.id = id;
		}

		public final long id;
		public boolean reviewed = false;
		public boolean childReviewed = false;
		public boolean root = false;
		public List<PageTree> subtrees = new LinkedList<PageTree>();
	};

	private PageTree reviewPageTree = new PageTree();
	private String pdfFile;

	public void setKey(String s) {
		spaceKey = s;
	}

	private String page;

	public String getPage() {
		return page;
	}

	private boolean forceClear = false;
	private Page thePage;

	public void setPage(String s) {
		page = s;
		try {
			thePage = pageManager.getPage(Long.parseLong(s));
		} catch (Exception e) {
		}
	}

	public void setForceClear(boolean b) {
		forceClear = b;
	}

	public void setReviewPath(String s) {
		reviewPath = s;
	}

	public void setReviewLabel(String s) {
		reviewLabel = s;
	}

	public void setReviewPages(String s) {
		try {

			String[] pages = s.split(",");
			List<Long> pageIds = new LinkedList<Long>();
			for (String p : pages)
				pageIds.add(new Long(p));

			while (!pageIds.isEmpty())
				reviewPageTree.subtrees.add(createTreeByRelevance(pageIds,
						pageIds.get(0)));

		} catch (Exception e) {
			log(e.toString());
			StringWriter stack = new StringWriter();
			e.printStackTrace(new PrintWriter(stack));
			error = e.toString() + "\n" + stack.toString();
		}
	}

	PageTree createTreeByRelevance(List<Long> pageIds, Long pageId) {
		log("createTreeByRelevance(" + pageId + ")");

		PageTree rc = null;

		if (pageIds.contains(new Long(pageId))) {
			rc = new PageTree(pageId.longValue());
			rc.reviewed = true;
			pageIds.remove(pageId);
		}

		if (pageIds.isEmpty())
			return rc;

		Page page = pageManager.getPage(pageId);

		if (page == null || !page.hasChildren())
			return rc;

		List<Page> children = page.getChildren();

		List<PageTree> subtrees = new LinkedList<PageTree>();

		boolean directChildReviewed = false;

		for (Page p : children) {
			PageTree t = createTreeByRelevance(pageIds, new Long(p.getId()));

			if (t != null && (t.reviewed || t.childReviewed)) {
				if (rc == null)
					rc = new PageTree(pageId.longValue());
				rc.childReviewed = true;
				if (t.reviewed)
					directChildReviewed = true;
				subtrees.add(t);
			} else {
				subtrees.add(new PageTree(p.getId()));
			}
		}
		if (rc != null && rc.childReviewed) {
			if (directChildReviewed)
				rc.subtrees = subtrees;
			else
				for (PageTree t : subtrees)
					if (t.childReviewed)
						rc.subtrees.add(t);
		}
		return rc;
	}

	String wikiFormatTree(PageTree t, String level) {
		log("wikiFormatTree(" + t.id + ", '" + level + "')");
		String rc = "";
		if (!t.root) {
			Page page = pageManager.getPage(t.id);
			if (t.reviewed)
				rc = level + " [" + page.getTitle() + " ^(v"
						+ page.getVersion() + ")^" + "|$" + t.id + "]\n";
			else
				rc = level + " {color:gray}" + page.getTitle()
						+ " ^(omitted)^{color}\n";
		}
		if (!t.subtrees.isEmpty()) {
			level += "-";
			for (PageTree st : t.subtrees)
				rc += wikiFormatTree(st, level);
		}
		return rc;
	}

	final static SimpleDateFormat tagDateFormat = new SimpleDateFormat(
			"yyyyMMdd-HHmmss");

	void markReviewedPages(PageTree t, String reviewPage, String reviewId,
			Date date) {
		log("markReviewedPages(" + t.id + ", '" + reviewPage + "', '"
				+ reviewId + "')");
		if (!t.root) {
			Page page = pageManager.getPage(t.id);
			if (t.reviewed) {
				contentPropertyManager.setStringProperty(page,
						"pdfreview.lastReviewedOn", tagDateFormat.format(date));

				String reviewHistory = contentPropertyManager.getTextProperty(
						page, "pdfreview.reviewHistory");
				if (reviewHistory == null || reviewHistory.isEmpty())
					reviewHistory = "";
				else
					reviewHistory = "," + reviewHistory;

				contentPropertyManager.setTextProperty(page,
						"pdfreview.reviewHistory", reviewPage + ":" + reviewId
								+ reviewHistory);
			}
		}
		if (!t.subtrees.isEmpty()) {
			for (PageTree st : t.subtrees)
				markReviewedPages(st, reviewPage, reviewId, date);
		}
	}

	public void setFile(String s) {
		pdfFile = s;
	}

	private String upgrade;

	public void setUpgrade(String upgrade) {
		this.upgrade = upgrade;
	}

	ManageReviewLogs(BootstrapManager bootstrapManager,
			PageManager pageManager, AttachmentManager attachmentManager,
			SpaceManager spaceManager,
			ContentPropertyManager contentPropertyManager) {
		this.bootstrapManager = bootstrapManager;
		this.pageManager = pageManager;
		this.spaceManager = spaceManager;
		this.contentPropertyManager = contentPropertyManager;
	}

	String logString;

	void log(String s) {
		logString += "<li>" + s + "</li>";
	}

	public String xyz() {
		User user = AuthenticatedUserThreadLocal.getUser();
		String greeting = "Flurb";
		if (user != null) {
			greeting = "Hello " + user.getFullName() + "<br><br>";
		}
		return "<ul>" + "<li>Log:<ul>" + logString + "</ul></li>" + "<li>"
				+ bootstrapManager.getConfluenceHome() + "</li>" + "<li>"
				+ spaceKey + "</li>" + "<li>" + reviewPath + "</li>" + "<li>"
				+ reviewLabel + "</li>" + "<li>"
				+ wikiFormatTree(reviewPageTree, "") + "</li>" + "<li>"
				+ pdfFile + "</li>" + "<li>" + done + "</li>" + "<li>"
				+ greeting + "</li>" + "</ul>";
	}

	private boolean done = false;

	private String error;
	private String redirect;

	public String getError() {
		return error;
	}

	public String getRedirect() {
		return redirect;
	}

	public String execute() {
		if (upgrade != null) {
			for (Page p : (List<Page>) pageManager.getPages(
					spaceManager.getSpace(upgrade), false)) {
				try {
					String s = contentPropertyManager.getStringProperty(p,
							"pdfreview.reviewHistory");
					if (s != null) {
						contentPropertyManager.removeProperty(p,
								"pdfreview.reviewHistory");
						contentPropertyManager.setTextProperty(p,
								"pdfreview.reviewHistory", s);
					}
				} catch (Exception e) {
				}
			}
			error = "Done upgrade";
			return "input";
		}

		if (thePage == null || forceClear == false)
			return "input";

		if (forceClear) {
			User user = AuthenticatedUserThreadLocal.getUser();
			if (user != null
					&& (user.getName().equals("abutcher") || user.getName()
							.equals("ajb"))) {
				contentPropertyManager.removeProperty(thePage,
						"pdfreview.lastReviewedOn");
				contentPropertyManager.removeProperty(thePage,
						"pdfreview.reviewHistory");
				redirect = thePage.getUrlPath();
				return "success";
			}
		}

		if (error == null)
			return "input";

		return "error";
	}
}
