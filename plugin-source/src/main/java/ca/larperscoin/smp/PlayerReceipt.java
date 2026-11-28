package ca.larperscoin.smp;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.zip.GZIPInputStream;

/** Read-only verification of the player PDC receipt in the actual compressed player save. */
final class PlayerReceipt {
    private static final int LIMIT=16*1024*1024;
    private PlayerReceipt(){}
    static boolean exists(Path file,String key)throws IOException{
        byte[] bytes;
        try(var stream=new GZIPInputStream(Files.newInputStream(file))){bytes=stream.readNBytes(LIMIT+1);}
        if(bytes.length>LIMIT)throw new IOException("Player NBT too large");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readUnsignedByte()!=10)throw new IOException("Invalid NBT root");
            in.readUTF();
            boolean found=false;
            for(int type;(type=in.readUnsignedByte())!=0;){
                String name=in.readUTF();
                if(type==10&&name.equals("BukkitValues")){
                    for(int entry;(entry=in.readUnsignedByte())!=0;){
                        String field=in.readUTF();
                        if(entry==1&&field.equals(key)){if(in.readByte()==1)found=true;}
                        else skip(in,entry,1);
                    }
                }else skip(in,type,1);
            }
            if(!found)return false;
        }
        // Flush the independently read file as well as the journal.
        try(var channel=FileChannel.open(file,StandardOpenOption.WRITE)){channel.force(true);}
        return true;
    }
    private static int count(DataInputStream in)throws IOException{
        int count=in.readInt();if(count<0||count>LIMIT)throw new IOException("Invalid NBT length");return count;
    }
    private static void skip(DataInputStream in,int type,int depth)throws IOException{
        if(depth>64)throw new IOException("NBT nesting too deep");
        switch(type){
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3,5 -> in.skipNBytes(4);
            case 4,6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(count(in));
            case 8 -> in.skipNBytes(in.readUnsignedShort());
            case 9 -> {int entry=in.readUnsignedByte(),n=count(in);if(entry==0&&n!=0)throw new IOException("Invalid NBT list");for(int i=0;i<n;i++)skip(in,entry,depth+1);}
            case 10 -> {for(int entry;(entry=in.readUnsignedByte())!=0;){in.readUTF();skip(in,entry,depth+1);}}
            case 11 -> in.skipNBytes(4L*count(in));
            case 12 -> in.skipNBytes(8L*count(in));
            default -> throw new IOException("Invalid NBT tag "+type);
        }
    }
}
