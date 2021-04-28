package cn.k8ops.ant;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        PipelineTask pipelineTask = new PipelineTask();
        pipelineTask.setFile(args[0]);
        pipelineTask.execute();
        System.out.println( "Hello World!" );
    }
}
