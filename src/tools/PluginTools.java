package tools;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import entity.Element;
import org.apache.http.util.TextUtils;

import java.awt.*;
import java.io.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PluginTools {

    /**
     * 获取编辑对象,在Action的actionPerformed方法里使用
     *
     * @param e
     * @return
     */
    public static Editor getEditor(AnActionEvent e) {
        return e.getData(PlatformDataKeys.EDITOR);
    }

    public static Project getProject(AnActionEvent e) {
        return e.getProject();
    }

    public static PsiFile getTargetFile(AnActionEvent e) {
        return e.getData(LangDataKeys.PSI_FILE);
    }

    /**
     * 获取修改类文件的工厂对象
     *
     * @param project
     * @return
     */
    private static PsiElementFactory getPsiElementFactory(Project project) {
        return JavaPsiFacade
                .getElementFactory(project);
    }

    // 通过strings.xml获取的值
    private static String StringValue;

    /**
     * 显示dialog
     *
     * @param editor
     * @param result 内容
     * @param time   显示时间，单位秒
     */
    public static void showPopupBalloon(final Editor editor, final String result, final int time) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                JBPopupFactory factory = JBPopupFactory.getInstance();
                factory.createHtmlTextBalloonBuilder(result, null,
                        new JBColor(new Color(116, 214, 238), new Color(76, 112, 117)),
                        null)
                        .setFadeoutTime(time * 1000)
                        .createBalloon()
                        .show(factory.guessBestPopupLocation(editor), Balloon.Position.below);
            }
        });
    }

    /**
     * 驼峰
     *
     * @param fieldName
     * @return
     */
    public static String getFieldName(String fieldName) {
        if (!TextUtils.isEmpty(fieldName)) {
            String[] names = fieldName.split("_");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < names.length; i++) {
                sb.append(firstToUpperCase(names[i]));
            }
            fieldName = sb.toString();
        }
        return fieldName;
    }

    /**
     * 第一个字母大写
     *
     * @param key
     * @return
     */
    public static String firstToUpperCase(String key) {
        return key.substring(0, 1).toUpperCase(Locale.CHINA) + key.substring(1);
    }

    /**
     * 解析xml获取string的值
     *
     * @param psiFile
     * @param text
     * @return
     */
    public static String getTextFromStringsXml(PsiFile psiFile, String text) {
        psiFile.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                super.visitElement(element);
                if (element instanceof XmlTag) {
                    XmlTag tag = (XmlTag) element;
                    if (tag.getName().equals("string")
                            && tag.getAttributeValue("name").equals(text)) {
                        PsiElement[] children = tag.getChildren();
                        String value = "";
                        for (PsiElement child : children) {
                            value += child.getText();
                        }
                        // value = <string name="app_name">My Application</string>
                        // 用正则获取值
                        Pattern p = Pattern.compile("<string name=\"" + text + "\">(.*)</string>");
                        Matcher m = p.matcher(value);
                        while (m.find()) {
                            StringValue = m.group(1);
                        }
                    }
                }
            }
        });
        return StringValue;
    }

    /**
     * 获取所有id
     *
     * @param file
     * @param elements
     * @return
     */
    public static java.util.List<Element> getIDsFromLayout(final PsiFile file, final java.util.List<Element> elements) {
        // To iterate over the elements in a file
        // 遍历一个文件的所有元素
        file.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                super.visitElement(element);
                // 解析Xml标签
                if (element instanceof XmlTag) {
                    XmlTag tag = (XmlTag) element;
                    // 获取Tag的名字（TextView）或者自定义
                    String name = tag.getName();
                    // 如果有include
                    if (name.equalsIgnoreCase("include")) {
                        // 获取布局
                        XmlAttribute layout = tag.getAttribute("layout", null);
                        // 获取project
                        Project project = file.getProject();
                        // 布局文件
                        XmlFile include = null;
                        PsiFile[] psiFiles = FilenameIndex.getFilesByName(project, getLayoutName(layout.getValue()) + ".xml", GlobalSearchScope.allScope(project));
                        if (psiFiles.length > 0) {
                            include = (XmlFile) psiFiles[0];
                        }
                        if (include != null) {
                            // 递归
                            getIDsFromLayout(include, elements);
                            return;
                        }
                    }
                    // 获取id字段属性
                    XmlAttribute id = tag.getAttribute("android:id", null);
                    if (id == null) {
                        return;
                    }
                    // 获取id的值
                    String idValue = id.getValue();
                    if (idValue == null) {
                        return;
                    }
                    XmlAttribute aClass = tag.getAttribute("class", null);
                    if (aClass != null) {
                        name = aClass.getValue();
                    }
                    // 添加到list
                    try {
                        Element e = new Element(name, idValue, tag);
                        elements.add(e);
                    } catch (IllegalArgumentException e) {

                    }
                }
            }
        });


        return elements;
    }

    /**
     * layout.getValue()返回的值为@layout/layout_view
     *
     * @param layout
     * @return
     */
    public static String getLayoutName(String layout) {
        if (layout == null || !layout.startsWith("@") || !layout.contains("/")) {
            return null;
        }
        // @layout layout_view
        String[] parts = layout.split("/");
        if (parts.length != 2) {
            return null;
        }
        // layout_view
        return parts[1];
    }

    /**
     * 根据当前文件获取对应的class文件
     *
     * @param editor
     * @param file
     * @return
     */
    public static PsiClass getTargetClass(Editor editor, PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        } else {
            PsiClass target = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            return target instanceof SyntheticElement ? null : target;
        }
    }


    /**
     * 添加接口实现
     *
     * @param project        工程对象
     * @param rootClass      当前文件对象
     * @param interfaceClass 要实现的接口
     */
    public static void addImpl(Project project, PsiClass rootClass, PsiClass interfaceClass) {
        PsiElementFactory factory = getPsiElementFactory(project);
        PsiJavaCodeReferenceElement ref = factory.createClassReferenceElement(interfaceClass);


        PsiReferenceList implementsList = rootClass.getImplementsList();
        PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
        if (referenceElements != null && referenceElements.length > 0) {
            for (int i = 0; i < referenceElements.length; i++) {
                if (referenceElements[i].getQualifiedName().equals(ref.getQualifiedName())) {
                    //已存在,不添加
                    return;
                }
            }
        }
        implementsList.add(ref);
    }

    /**
     * 添加包含(import)
     *
     * @param project     工程对象
     * @param rootClass   根class对象
     * @param importClass 要包含的class对象
     */
    public static void addImport(Project project, PsiClass rootClass, PsiClass importClass) {
        //生成import对象.
        PsiElementFactory factory = getPsiElementFactory(project);
        PsiImportStatement importStatement = factory
                .createImportStatement(importClass);

        PsiImportList importList = ((PsiJavaFile) rootClass.getContainingFile()).getImportList();
        if (importList != null && importList.getImportStatements() != null && importList.getImportStatements().length > 0) {
            PsiImportStatement[] importStatements = importList.getImportStatements();
            for (int i = 0; i < importStatements.length; i++) {
                if (importStatements[i].getQualifiedName().equals(importStatement.getQualifiedName())) {
                    //已存在,不添加
                    return;
                }
            }
        }
        importList.add(importStatement);
    }


    public static String showError(Editor mEditor, Exception ex) {
        String errorInfo = "生成错误信息失败";
        ByteArrayOutputStream baos = null;
        PrintStream printStream = null;
        try {
            baos = new ByteArrayOutputStream();
            printStream = new PrintStream(baos);
            ex.printStackTrace(printStream);
            byte[] data = baos.toByteArray();
            errorInfo = new String(data);
            data = null;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (printStream != null)
                    printStream.close();
                if (baos != null)
                    baos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 打印异常信息
        showPopupBalloon(mEditor, errorInfo, 10);
        return errorInfo;
    }

    /**
     * 把字符串数据写入文件
     *
     * @param content 需要写入的字符串
     * @param path    文件路径名称
     * @param append  是否以添加的模式写入
     * @return 是否写入成功
     */
    public static boolean writeFile(byte[] content, String path, boolean append) {
        boolean res = false;
        File f = new File(path);
        RandomAccessFile raf = null;
        try {
            if (f.exists()) {
                if (!append) {
                    f.delete();
                    f.createNewFile();
                }
            } else {
                f.createNewFile();
            }
            if (f.canWrite()) {
                raf = new RandomAccessFile(f, "rw");
                raf.seek(raf.length());
                raf.write(content);
                res = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(raf);
        }
        return res;
    }
    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            closeable = null;
        }
    }

}
