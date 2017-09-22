package tools;

import View.FindViewByIdDialog;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.command.WriteCommandAction.Simple;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import entity.Element;
import org.apache.http.util.TextUtils;

import java.util.List;

/**
 * 最终生成具体代码
 */
public class ViewFieldMethodCreator extends Simple {

    public static final String CALL_ENTRA = "initView";//自己框架初始化View的入口方法

    public static final String ROOT_METHOD = "initViews";//插件生成的入口方法,查找所有View和设置点击事件的方法名
    public static final String FIND_VIEW_METHOD = "findView";//插件生成的查询控件的方法.
    public static final String SET_LISTENER_METHOD = "setViewOnClickListener";//给具体某个View设置点击事件的方法
    public static final String HANDLER_CLICK_METHOD = "onClick";//处理点击事件的方法名
    public static final String CLICK_WEAKCLASS = "ViewClickAction";
    private FindViewByIdDialog mDialog;
    private Editor mEditor;
    private PsiFile mFile;
    private Project mProject;
    private PsiClass mClass;
    private List<Element> mElements;
    private PsiElementFactory mFactory;

    public ViewFieldMethodCreator(FindViewByIdDialog dialog, Editor editor, PsiFile psiFile, PsiClass psiClass, String command, List<Element> elements, String selectedText) {
        super(psiClass.getProject(), command);
        mDialog = dialog;
        mEditor = editor;
        mFile = psiFile;
        mProject = psiClass.getProject();
        mClass = psiClass;
        mElements = elements;
        // 获取Factory
        mFactory = JavaPsiFacade.getElementFactory(mProject);
    }

    @Override
    protected void run() throws Throwable {
        try {
            importViewClass();
            generateFields();
            generateOnClickMethod();
        } catch (Exception e) {
            // 异常打印
            mDialog.cancelDialog();
            PluginTools.showError(mEditor, e);
            return;
        }
        // 重写class
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
        styleManager.optimizeImports(mFile);
        styleManager.shortenClassReferences(mClass);
        new ReformatCodeProcessor(mProject, mClass.getContainingFile(), null, false).runWithoutProgress();
        PluginTools.showPopupBalloon(mEditor, "生成成功", 2);
    }


    //region 创建成员变量和findView方法.

    /**
     * 添加View的包含
     */
    private void importViewClass() {
        GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
        PsiClass[] psiClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName("View", searchScope);
        if (psiClasses != null && psiClasses.length > 0) {
            for (int i = 0; i < psiClasses.length; i++) {
                String qualifiedName = psiClasses[i].getQualifiedName();
                if (qualifiedName.equals("android.view.View")) {
                    PluginTools.addImport(mProject, mClass, psiClasses[i]);
                }
            }
        }
    }

    /**
     * 创建findView方法.
     */
    private void createFindViewMethod() {
        PsiMethod method = mFactory.createMethodFromText(
                "protected <T> T " + FIND_VIEW_METHOD + "(int id){\nreturn (T)findViewById(id);\n}", mClass);
        PsiMethod methodBySignature = mClass.findMethodBySignature(method, true);
        if (methodBySignature != null) {//当前类有这个方法,不用创建
            return;
        }
        //如果当前类没有这个方法,遍历父类
        PsiClass superClass = mClass.getSuperClass();
        int i = 0;
        while (superClass != null) {
            PsiMethod[] findViews = superClass.findMethodsByName(FIND_VIEW_METHOD, true);
            if (findViews != null && findViews.length > 0) {//父类有同名方法
                for (int i1 = 0; i1 < findViews.length; i1++) {//判断同名方法的参数类型是否一致
                    PsiParameterList parameterList = findViews[i1].getParameterList();
                    if (parameterList != null && parameterList.getParameters() != null) {
                        PsiParameter[] parameters = parameterList.getParameters();
                        if (parameters != null
                                && parameters.length == 1
                                && parameters[0].getType() == PsiType.INT) {
                            return;
                        }
                    }
                }
            }
            //继续找上一层父类
            superClass = superClass.getSuperClass();
        }
        //走到这里代表父类没有这个方法,添加
        mClass.add(method);
    }

    /**
     * 创建变量,查找变量,给View设置点击事件
     */
    private void generateFields() {
        createFindViewMethod();

        StringBuilder fromText = new StringBuilder();

        fromText.append("private void " + ROOT_METHOD + "(){\n");
        //region 遍历所有字段,创建变量,创建findView方法.
        for (Element element : mElements) {
            createField(element);
            if (!element.isCreateFiled()) {//不创建变量就直接返回
                continue;
            }
            fromText.append(element.getFieldName());
            fromText.append("=");
            fromText.append(FIND_VIEW_METHOD + "(" + element.getFullID() + ");\n");
        }
        //endregion

        //region 添加设置监听事件方法
        for (Element mElement : mElements) {
            if (mElement.isCreateClickMethod()) {
                fromText.append(SET_LISTENER_METHOD + "((View)" + FIND_VIEW_METHOD + "("
                        + mElement.getFullID() + "));\n");
            }
        }
        //endregion


        fromText.append("}");
        // 添加到class
        PsiMethod method2 = mFactory.createMethodFromText(fromText.toString(), mClass);
        PsiMethod methodBySignature = mClass.findMethodBySignature(method2, true);
        if (methodBySignature != null) {
            methodBySignature.replace(method2);
        } else {
            mClass.add(method2);
        }

        //region 添加调用方法
        //首先找initView方法

        PsiMethod[] initViews = mClass.findMethodsByName(CALL_ENTRA, true);
        if (initViews != null && initViews.length > 0) {
            callInitViews(initViews[0]);
            return;
        }
    }

    /**
     * 创建变量
     *
     * @param element
     */
    private void createField(Element element) {
        if (element == null || !element.isCreateFiled()) {//不存在或者不生成,直接返回
            return;
        }
        if (mClass.findFieldByName(element.getFieldName(), true) != null) {//判断变量是否已存在
            return;
            // 创建新的变量
        }
        // 设置变量名，获取text里面的内容
        String text = element.getXml().getAttributeValue("android:text");
        if (TextUtils.isEmpty(text)) {
            // 如果是text为空，则获取hint里面的内容
            text = element.getXml().getAttributeValue("android:hint");
        }
        // 如果是@string/app_name类似
        if (!TextUtils.isEmpty(text) && text.contains("@string/")) {
            text = text.replace("@string/", "");
            // 获取strings.xml
            PsiFile[] psiFiles = FilenameIndex.getFilesByName(mProject, "strings.xml", GlobalSearchScope.allScope(mProject));
            if (psiFiles.length > 0) {
                for (PsiFile psiFile : psiFiles) {
                    // 获取src\main\res\values下面的strings.xml文件
                    String dirName = psiFile.getParent().toString();
                    if (dirName.contains("src\\main\\res\\values")) {
                        text = PluginTools.getTextFromStringsXml(psiFile, text);
                    }
                }
            }
        }
        String fieldStr = element.getViewName() + " " + element.getFieldName() + ";";
        if (!TextUtils.isEmpty(text)) {
            fieldStr = "/****" + text + "****/\n" + fieldStr;
        }
        mClass.add(mFactory.createFieldFromText(fieldStr, mClass));

    }

    /**
     * 在主程序中调用initView方法
     *
     * @param psiMethod
     */
    private void callInitViews(PsiMethod psiMethod) {
        String text = psiMethod.getBody().getText();
        if (text.contains(ROOT_METHOD + "()")) {
        } else {
            StringBuilder sb = new StringBuilder(text);
            sb.insert((sb.length() - 1 < 0 ? 0 : sb.length() - 1), ROOT_METHOD + "();");
            PsiCodeBlock codeBlockFromText = mFactory.createCodeBlockFromText(sb.toString(), psiMethod);
            psiMethod.getBody().replace(codeBlockFromText);
        }
    }


    //endregion


    //region 创建点击事件

    /**
     * 创建OnClick方法
     */
    private void generateOnClickMethod() {
        PsiMethod method;
        if (isCanRxView()) {
            implRxView();
            method = mFactory.createMethodFromText(
                    "protected void " + SET_LISTENER_METHOD + "(View v){RxView.clicks(v).throttleFirst(500, TimeUnit.MILLISECONDS).subscribe(new " + CLICK_WEAKCLASS + "(this, v));\n}", mClass);
        } else {
            implNormal();
            method = mFactory.createMethodFromText(
                    "protected void " + SET_LISTENER_METHOD + "(View v){v.setOnClickListener(this);}", mClass);
        }
        PsiMethod methodBySignature = mClass.findMethodBySignature(method, true);
        if (methodBySignature != null) {
            methodBySignature.replace(method);
        } else {
            mClass.add(method);
        }
    }
    /**
     * 创建内部类
     *
     * @param searchScope
     * @param ref
     * @param importStatement
     */
    private void createInClass(GlobalSearchScope searchScope, PsiClass ref, PsiClass importStatement) {
        PsiClass viewClickAction;
        PsiClass[] psiClasses;
        viewClickAction = mFactory.createClassFromText("private WeakReference<" + mClass.getName() + "> weakReference;\n" +
                "        private WeakReference<View> viewWeakReference;\n" +
                "\n" +
                "        ViewClickAction(" + mClass.getName() + " activity, View view) {\n" +
                "            weakReference = new WeakReference<" + mClass.getName() + ">(activity);\n" +
                "            viewWeakReference = new WeakReference<View>(view);\n" +
                "        }\n" +
                "\n" +
                "        @Override\n" +
                "        public void call(Object o) {\n" +
                "            View view = viewWeakReference.get();\n" +
                "            " + mClass.getName() + " activity = weakReference.get();\n" +
                "            if (activity == null || view == null) {\n" +
                "                return;\n" +
                "            }\n" +
                "            activity." + HANDLER_CLICK_METHOD + "(view);\n" +
                "        }", mClass);
        viewClickAction.setName(CLICK_WEAKCLASS);
        viewClickAction.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
        viewClickAction.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
        PluginTools.addImport(mProject, mClass, importStatement);
        PluginTools.addImpl(mProject, viewClickAction, ref);
        //region 添加弱引用类的引用
        psiClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName("WeakReference", searchScope);
        if (psiClasses != null && psiClasses.length > 0) {
            for (int i1 = 0; i1 < psiClasses.length; i1++) {
                if (psiClasses[i1].getQualifiedName().equals("java.lang.ref.WeakReference")) {
                    PluginTools.addImport(mProject, mClass, psiClasses[i1]);
                }
            }
        }
        //endregion
        mClass.add(viewClickAction);
    }
    /**
     * 是否可以使用RxView
     *
     * @return
     */
    private boolean isCanRxView() {
        GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
        PsiClass[] psiClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName("RxView", searchScope);
        if (psiClasses != null && psiClasses.length > 0) {//使用RxView的OnClick方法.
            return true;
        } else {//使用默认onClick方法
            return false;
        }


    }


    private void implRxView() {
        GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
        PsiClass[] psiClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName("View", searchScope);
        if (psiClasses != null && psiClasses.length > 0) {//使用RxView的OnClick方法.
            //region 继承Action1接口
            psiClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName("Action1", searchScope);
            if (psiClasses != null && psiClasses.length > 0) {
                for (int i = 0; i < psiClasses.length; i++) {
                    if (psiClasses[i].getQualifiedName().equals("rx.functions.Action1")) {
                        PsiClass viewClickAction = mClass.findInnerClassByName(CLICK_WEAKCLASS, true);
                        if (viewClickAction != null) {
                            //PluginTools.showPopupBalloon(mEditor, "内部类已存在", 3);
                        } else {
                            createInClass(searchScope, psiClasses[i], psiClasses[i]);
                        }
                        //创建onViewClick方法
                        createOnClickMethod();
                        return;
                    }
                }
            }
        }
        //走到这里是添加失败,添加默认方法
        implNormal();
    }

    private void implNormal() {
        GlobalSearchScope searchScope = GlobalSearchScope.allScope(mProject);
        PsiClass[] psiClasses = PsiShortNamesCache.getInstance(mProject).getClassesByName("View", searchScope);
        if (psiClasses != null && psiClasses.length > 0) {
            for (int i = 0; i < psiClasses.length; i++) {
                String qualifiedName = psiClasses[i].getQualifiedName();
                if (qualifiedName.equals("android.view.View")) {
                    PsiClass onClickListener = psiClasses[i].findInnerClassByName("OnClickListener", true);
                    createOnClickMethod();
                    PluginTools.addImport(mProject, mClass, onClickListener);
                    PluginTools.addImpl(mProject, mClass, onClickListener);
                }
            }
        }
    }

    /**
     * 创建默认的onClick方法
     */
    private void createOnClickMethod() {
        //先判断是否已实现onClick方法
        PsiMethod method = mFactory.createMethodFromText("public void " + HANDLER_CLICK_METHOD + "(View view) {}", mClass);
        PsiMethod onClick = mClass.findMethodBySignature(method, true);
        StringBuilder sb = new StringBuilder();
        int insertPosition = 0;
        if (onClick != null && mClass.getText().contains("void onClick(View")) {//方法已存在,先把原来的代码全部添加进来
            PsiCodeBlock body = onClick.getBody();
            if (body != null) {
                String text = body.getText();
                if (text != null) {
                    sb.append(text);
                }
            }

        }
        if (sb.indexOf("switch") > -1) {//如果有switch字段,表示已有,插入case即可
            insertPosition = sb.indexOf("default:") - 1;
            if (insertPosition < 0) {
                insertPosition = sb.indexOf("{", sb.indexOf("switch")) + 1;
            }
        } else {//如果没有switch字段,需要插入,但是要判断插入位置(已有代码的话从第一个字开始插
            if (sb.length() == 0) {//空的,前面带上{,结尾带上}
                sb.append("{switch (view.getId()){\n");
                insertPosition = sb.length() - 1;
                sb.append("default");
                sb.append(":\nbreak;\n");
                sb.append("}}");
            } else {//不是空的,改为插入
                sb.insert(1, "switch (view.getId()){\ndefault:\nbreak;\n}");
                insertPosition = sb.indexOf("default:") - 1;
            }
        }

        for (Element element : mElements) {
            // 可以使用并且可以点击
            if (element != null && element.isCreateClickMethod() && sb.indexOf(element.getFullID()) == -1) {
                sb.insert(insertPosition, "case " + element.getFullID() + ":\nbreak;\n");
            }
        }
        String toString = sb.toString();
        toString = toString.replace("switch (v.getId())", "switch (view.getId())");
        method = mFactory.createMethodFromText("public void " + HANDLER_CLICK_METHOD + "(View view) " + toString + "", mClass);
        if (onClick != null && mClass.getText().contains("void onClick(View")) {
            onClick.replace(method);
        } else {
            mClass.add(method);
        }

    }


    //endregion
}
