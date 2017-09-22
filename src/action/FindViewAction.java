package action;

import View.FindViewByIdDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlFile;
import entity.Element;
import org.apache.http.util.TextUtils;
import tools.PluginTools;

import java.util.ArrayList;
import java.util.List;

public class FindViewAction extends AnAction {
    private FindViewByIdDialog mDialog;
    private String mSelectedText;

    /**
     * 1.获取到光标选中位置,解析到布局文件名称
     * 2.全局搜索到布局文件
     * 3.解析布局文件,获取到所有指定了ID的控件
     * 4.弹对话框供选择哪些控件需要生成代码.
     *
     * @param e
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
        // 获取project
        Project project = e.getProject();
        // 获取选中内容
        final Editor mEditor = e.getData(PlatformDataKeys.EDITOR);
        if (null == mEditor) {
            return;
        }
        SelectionModel model = mEditor.getSelectionModel();
        mSelectedText = model.getSelectedText();
        // 未选中布局内容，显示dialog
        if (TextUtils.isEmpty(mSelectedText)) {
            //region获取光标所在位置的布局,获取不到就让用户输入layout文件名,还是没有就直接退出
            mSelectedText = getCurrentLayout(mEditor);
            if (TextUtils.isEmpty(mSelectedText)) {
                mSelectedText = Messages.showInputDialog(project, "布局内容：（不需要输入R.layout.）", "未选中布局内容，请输入layout文件名", Messages.getInformationIcon());
                if (TextUtils.isEmpty(mSelectedText)) {
                    PluginTools.showPopupBalloon(mEditor, "未输入layout文件名", 5);
                    return;
                }
            }
            //endregion
        }
        //region 获取布局文件名后,通过FilenameIndex.getFilesByName全局搜索,获取布局文件对象，
        // GlobalSearchScope.allScope(project)搜索整个项目
        PsiFile[] psiFiles = FilenameIndex.getFilesByName(project, mSelectedText + ".xml", GlobalSearchScope.allScope(project));
        if (psiFiles.length <= 0) {
            PluginTools.showPopupBalloon(mEditor, "未找到选中的布局文件" + mSelectedText, 5);
            return;
        }
        //endregion

        //region 解析布局文件
        XmlFile xmlFile = (XmlFile) psiFiles[0];
        List<Element> elements = new ArrayList<>();
        PluginTools.getIDsFromLayout(xmlFile, elements);
        //endregion

        //region 如果找到有ID,就弹对话框供用户选择
        if (elements.size() != 0) {
            PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(mEditor, project);
            PsiClass psiClass = PluginTools.getTargetClass(mEditor, psiFile);
            // 有的话就创建变量和findViewById
            if (mDialog != null && mDialog.isShowing()) {
                mDialog.cancelDialog();
            }
            mDialog = new FindViewByIdDialog(mEditor, project, psiFile, psiClass, elements, mSelectedText);
            mDialog.showDialog();
        } else {
            PluginTools.showPopupBalloon(mEditor, "layout文件中未找到任何Id", 5);
        }
        //endregion
    }

    /**
     * 获取当前光标的layout文件名称
     */
    private String getCurrentLayout(Editor editor) {
        Document document = editor.getDocument();
        CaretModel caretModel = editor.getCaretModel();
        int caretOffset = caretModel.getOffset();
        int lineNum = document.getLineNumber(caretOffset);
        int lineStartOffset = document.getLineStartOffset(lineNum);
        int lineEndOffset = document.getLineEndOffset(lineNum);
        String lineContent = document.getText(new TextRange(lineStartOffset, lineEndOffset));
        String layoutMatching = "R.layout.";
        if (!TextUtils.isEmpty(lineContent) && lineContent.contains(layoutMatching)) {
            // 获取layout文件的字符串
            int startPosition = lineContent.indexOf(layoutMatching) + layoutMatching.length();
            //开始位置找到了,布局文件名字结尾只有五种情况
            // ")"
            //"]"
            //"}"
            //","
            //";"
            String[] endStr = {"}", "]", ")", ",", ";"};
            int endPosition = lineContent.length();
            for (int i = 0; i < endStr.length; i++) {
                int temp = lineContent.indexOf(endStr[i], startPosition);
                if (temp > -1 && temp < endPosition) {
                    endPosition = temp;
                }
            }
            String layoutStr = lineContent.substring(startPosition, endPosition);
            return layoutStr;
        }
        return null;
    }
}
