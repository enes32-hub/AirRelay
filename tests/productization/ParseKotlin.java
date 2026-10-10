import java.nio.file.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.cli.jvm.compiler.*;
import org.jetbrains.kotlin.config.*;
import org.jetbrains.kotlin.psi.*;
public class ParseKotlin {
 public static void main(String[] args) throws Exception {
  Disposable d=Disposer.newDisposable();
  CompilerConfiguration cfg=new CompilerConfiguration();
  cfg.put(CommonConfigurationKeys.MODULE_NAME,"source-parse-only");
  KotlinCoreEnvironment env=KotlinCoreEnvironment.createForProduction(d,cfg,EnvironmentConfigFiles.JVM_CONFIG_FILES);
  KtPsiFactory factory=new KtPsiFactory(env.getProject(),false);
  int count=0, failures=0;
  try(var stream=Files.walk(Path.of(args[0]))) {
   for(Path p:stream.filter(x->x.toString().endsWith(".kt")).toList()) {
    count++;
    KtFile f=factory.createFile(p.getFileName().toString(),Files.readString(p).replace("\r\n", "\n").replace("\r", "\n").replace("\uFEFF", ""));
    for(PsiErrorElement e:PsiTreeUtil.collectElementsOfType(f,PsiErrorElement.class)) {
     int line=1+(int)f.getText().substring(0,e.getTextOffset()).chars().filter(c->c=='\n').count();
     System.out.println("ERROR "+p+":"+line+" "+e.getErrorDescription());failures++;
    }
   }
  } finally { Disposer.dispose(d); }
  System.out.println("Kotlin syntax: "+count+" files, "+failures+" parser errors. Not an Android compile.");
  if(failures>0)System.exit(1);
 }
}
