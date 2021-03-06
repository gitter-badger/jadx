package jadx.gui.ui;

import jadx.api.CodePosition;
import jadx.gui.treemodel.JClass;
import jadx.gui.utils.Position;

import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;

import org.fife.ui.rsyntaxtextarea.LinkGenerator;
import org.fife.ui.rsyntaxtextarea.LinkGeneratorResult;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CodeArea extends RSyntaxTextArea {
	private static final Logger LOG = LoggerFactory.getLogger(CodeArea.class);

	private static final long serialVersionUID = 6312736869579635796L;

	public static final Color BACKGROUND = new Color(0xFAFAFA);
	public static final Color JUMP_TOKEN_FGD = new Color(0x491BA1);

	private final CodePanel codePanel;
	private final JClass cls;

	CodeArea(CodePanel panel) {
		this.codePanel = panel;
		this.cls = panel.getCls();

		setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
		SyntaxScheme scheme = getSyntaxScheme();
		scheme.getStyle(Token.FUNCTION).foreground = Color.BLACK;

		setMarkOccurrences(true);
		setBackground(BACKGROUND);
		setAntiAliasingEnabled(true);
		setEditable(false);
		Caret caret = getCaret();
		if (caret instanceof DefaultCaret) {
			((DefaultCaret) caret).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		}
		caret.setVisible(true);

		setHyperlinksEnabled(true);
		CodeLinkGenerator codeLinkProcessor = new CodeLinkGenerator(cls);
		setLinkGenerator(codeLinkProcessor);
		addHyperlinkListener(codeLinkProcessor);

		setText(cls.getCode());
	}

	private boolean isJumpToken(Token token) {
		if (token.getType() == TokenTypes.IDENTIFIER) {
			// fast skip
			if (token.length() == 1) {
				char ch = token.getTextArray()[token.getTextOffset()];
				if (ch == '.' || ch == ',' || ch == ';') {
					return false;
				}
			}
			Position pos = getPosition(cls, this, token.getOffset());
			if (pos != null) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Color getForegroundForToken(Token t) {
		if (isJumpToken(t)) {
			return JUMP_TOKEN_FGD;
		}
		return super.getForegroundForToken(t);
	}

	static Position getPosition(JClass jCls, RSyntaxTextArea textArea, int offset) {
		try {
			int line = textArea.getLineOfOffset(offset);
			int lineOffset = offset - textArea.getLineStartOffset(line);
			CodePosition pos = jCls.getCls().getDefinitionPosition(line + 1, lineOffset + 1);
			if (pos != null && pos.isSet()) {
				return new Position(pos);
			}
		} catch (BadLocationException e) {
			LOG.error("Can't get line by offset", e);
		}
		return null;
	}

	Position getCurrentPosition() {
		return new Position(cls, getCaretLineNumber() + 1);
	}

	Integer getSourceLine(int line) {
		return cls.getCls().getSourceLine(line);
	}

	void scrollToLine(int line) {
		int lineNum = line - 1;
		if (lineNum < 0) {
			lineNum = 0;
		}
		setCaretAtLine(lineNum);
		centerCurrentLine();
		forceCurrentLineHighlightRepaint();
	}

	public void centerCurrentLine() {
		JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
		if (viewport == null) {
			return;
		}
		try {
			Rectangle r = modelToView(getCaretPosition());
			if (r == null) {
				return;
			}
			int extentHeight = viewport.getExtentSize().height;
			int viewHeight = viewport.getViewSize().height;

			int y = Math.max(0, r.y - (extentHeight / 2));
			y = Math.min(y, viewHeight - extentHeight);

			viewport.setViewPosition(new Point(0, y));
		} catch (BadLocationException e) {
			LOG.debug("Can't center current line", e);
		}
	}

	private void setCaretAtLine(int line) {
		try {
			setCaretPosition(getLineStartOffset(line));
		} catch (BadLocationException e) {
			LOG.debug("Can't scroll to " + line, e);
		}
	}

	private class CodeLinkGenerator implements LinkGenerator, HyperlinkListener {
		private final JClass jCls;

		public CodeLinkGenerator(JClass cls) {
			this.jCls = cls;
		}

		@Override
		public LinkGeneratorResult isLinkAtOffset(RSyntaxTextArea textArea, int offset) {
			try {
				Token token = textArea.modelToToken(offset);
				if (token == null) {
					return null;
				}
				final int sourceOffset = token.getOffset();
				final Position defPos = getPosition(jCls, textArea, sourceOffset);
				if (defPos == null) {
					return null;
				}
				return new LinkGeneratorResult() {
					@Override
					public HyperlinkEvent execute() {
						return new HyperlinkEvent(defPos, HyperlinkEvent.EventType.ACTIVATED, null,
								defPos.getCls().getFullName());
					}

					@Override
					public int getSourceOffset() {
						return sourceOffset;
					}
				};
			} catch (Exception e) {
				LOG.error("isLinkAtOffset error", e);
				return null;
			}
		}

		@Override
		public void hyperlinkUpdate(HyperlinkEvent e) {
			Object obj = e.getSource();
			if (obj instanceof Position) {
				Position pos = (Position) obj;
				LOG.debug("Code jump to: {}", pos);
				TabbedPane tabbedPane = codePanel.getTabbedPane();
				tabbedPane.getJumpManager().addPosition(getCurrentPosition());
				tabbedPane.getJumpManager().addPosition(pos);
				tabbedPane.showCode(pos);
			}
		}
	}
}
