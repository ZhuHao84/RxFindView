# RxFindView
基于RxView的findview插件,如果项目依赖了RxView,则使用RxView的方式创建点击事件.否则使用默认的OnClickListener来创建点击事件


使用方法:<br/>
1.在代码中选中字符文件名,呼出Generate菜单选择RxFindView,或按快捷键alt+f<br/>
2.在弹出的对话框中勾选需要生成变量的ID即可生成变量名和findView方法<br/>
3.生成点击事件时,如果引用了RxView,会按照RxView的方式来生成点击事件.<br/>
4.如果没有引用RxView,会按照默认方式生成点击事件<br/>


注意事项:<br/>
1.当引入了RxView包时,插件会自动生成必要的内部类和方法来处理点击事件,不要使用跟插件生成的同名内部类,或者使用插件生成内部类后移植到父类中去,插件会自动遍历父类是否包含此内部类<br/><br/>
2.所有自动生成的查找View和设置点击事件的入口方法是 initViews(),不要使用同名方法,会被插件覆盖.<br/><br/>
3.本人工程中习惯封装一个initView()方法在自己封装的Activity或Fragment基类中调用,所以插件会在生成完入口方法后查找initView方法并自动添加调用,但如果当前类中没有找到名为initView的方法,则入口方法不会被调用,需要自己调用入口方法.<br/>
