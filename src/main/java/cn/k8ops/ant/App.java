package cn.k8ops.ant;

import cn.k8ops.ant.tasks.PipelineTask;

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
