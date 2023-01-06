package gitlet;
// 静态导入目标类的所有静态方法，并且可以直接使用方法名调用而不用类名.方法名
import static gitlet.Utils.*;
/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author TODO
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        // TODO: what if args is empty?
        // TODO: 使用异常机制
        // TODO: 使用异步GC
        if (args.length == 0) {
            exit("Please enter a command.");
        }
        String firstArg = args[0];
        switch(firstArg) {
            case "init":
                // TODO: handle the `init` command
                checkArgsValid(args, 1);
                Repository.init();
                break;
            case "add":
                // TODO: handle the `add [filename]` command
                checkArgsValid(args, 2);
                Repository.checkIfInitialized();
                Repository.add(args[1]);
                break;
            case "commit":
                checkArgsValid(args, 2);
                Repository.checkIfInitialized();
                Repository.commit(args[1]);
                break;
            case "rm":
                checkArgsValid(args, 2);
                Repository.checkIfInitialized();
                Repository.rm(args[1]);
                break;
            case "log":

                checkArgsValid(args, 1);
                Repository.checkIfInitialized();
                Repository.log();

                break;
            case "global-log":
                checkArgsValid(args, 1);
                Repository.checkIfInitialized();
                Repository.global_log();
                break;
            case "find":
                checkArgsValid(args, 2);
                Repository.checkIfInitialized();
                Repository.find(args[1]);
                break;
            case "status":
                checkArgsValid(args, 1);
                Repository.checkIfInitialized();
                Repository.status();
                break;
            case "checkout":
                Repository.checkIfInitialized();
                switch (args.length) {
                    case 2:
                        /* * checkout [branch name] */
                        Repository.checkoutBranch(args[1]);
                        break;
                    case 3:
                        if (!args[1].equals("--")) {
                            exit("Incorrect operands.");
                        }
                        /* * checkout -- [file name] */
                        Repository.checkout(args[2]);
                        break;
                    case 4:
                        if (!args[2].equals("--")) {
                            exit("Incorrect operands.");
                        }
                        /* * checkout [commit id] -- [file name] */
                        Repository.checkout(args[1], args[3]);
                        break;
                    default:
                        exit("Incorrect operands.");
                }
                break;
            case "branch":
                checkArgsValid(args, 2);
                Repository.checkIfInitialized();
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                checkArgsValid(args, 2);
                Repository.checkIfInitialized();
                Repository.rm_branch(args[1]);
                break;
            case "reset":
                checkArgsValid(args, 2);
                Repository.checkIfInitialized();
                Repository.reset(args[1]);
                break;
            default:
                exit("No command with that name exists.");
        }
    }

    public static void checkArgsValid(String[] args, int num){
        // todo: 使用异常机制
        if(args.length != num){
            exit("Incorrect operands.");
        }
    }
}
