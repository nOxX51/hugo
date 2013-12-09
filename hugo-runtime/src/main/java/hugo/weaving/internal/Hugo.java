package hugo.weaving.internal;

import android.util.Log;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;

import hugo.weaving.internal.util.Strings;

@Aspect
public class Hugo {
  private static final Pattern ANONYMOUS_CLASS = Pattern.compile("\\$\\d+$");

  @Pointcut("execution(@hugo.weaving.DebugLog * *(..))")
  public void method() {}

  @Pointcut("execution(@hugo.weaving.DebugLog *.new(..))")
  public void constructor() {}

  @Around("method() || constructor()")
  public Object logAndExecute(ProceedingJoinPoint joinPoint) throws Throwable {
    pushMethod(joinPoint);

    long startNanos = System.nanoTime();
    Object result = joinPoint.proceed();
    long stopNanos = System.nanoTime();
    long lengthMillis = TimeUnit.NANOSECONDS.toMillis(stopNanos - startNanos);

    popMethod(joinPoint, result, lengthMillis);

    return result;
  }

  private static void pushMethod(JoinPoint joinPoint) {
    CodeSignature codeSignature = (CodeSignature) joinPoint.getSignature();

    String className = codeSignature.getDeclaringTypeName();
    String methodName = codeSignature.getName();
    String[] parameterNames = codeSignature.getParameterNames();
    Object[] parameterValues = joinPoint.getArgs();

    StringBuilder builder = new StringBuilder("⇢ ");
    builder.append(methodName).append('(');
    for (int i = 0; i < parameterValues.length; i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(parameterNames[i]).append('=');
      appendObject(builder, parameterValues[i]);
    }
    builder.append(')');

    Log.d(asTag(className), builder.toString());
  }

  private static void popMethod(JoinPoint joinPoint, Object result, long lengthMillis) {
    Signature signature = joinPoint.getSignature();

    String className = signature.getDeclaringTypeName();
    String methodName = signature.getName();
    boolean hasReturnType = signature instanceof MethodSignature
        && ((MethodSignature) signature).getReturnType() != void.class;

    StringBuilder builder = new StringBuilder("⇠ ")
        .append(methodName)
        .append(" [")
        .append(lengthMillis)
        .append("ms]");

    if (hasReturnType) {
      builder.append(" = ");
      appendObject(builder, result);
    }

    Log.d(asTag(className), builder.toString());
  }

  private static void appendObject(StringBuilder builder, Object value) {
    builder.append(Strings.toString(value));
  }

  private static String asTag(String className) {
    Matcher m = ANONYMOUS_CLASS.matcher(className);
    if (m.find()) {
      className = m.replaceAll("");
    }
    return className.substring(className.lastIndexOf('.') + 1);
  }
}
