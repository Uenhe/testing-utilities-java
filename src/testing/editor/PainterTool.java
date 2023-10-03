package testing.editor;

import arc.func.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.editor.*;
import mindustry.game.*;
import mindustry.world.*;

import static testing.util.TUVars.*;

/** Mimics {@link EditorTool} */
public enum PainterTool{
    /** No tool is selected, for when you want to build. */
    none(KeyCode.v),
    pick(KeyCode.i){
        public void touched(int x, int y){
            if(!Structs.inBounds(x, y, painter.width(), painter.height())) return;

            Tile tile = painter.tile(x, y);
            painter.drawBlock = tile.block() == Blocks.air || !tile.block().inEditor ? tile.overlay() == Blocks.air ? tile.floor() : tile.overlay() : tile.block();
        }
    },
    line(KeyCode.l, "replace", "orthogonal"){

        @Override
        public void touchedLine(int x1, int y1, int x2, int y2){
            //straight
            if(mode == 1){
                if(Math.abs(x2 - x1) > Math.abs(y2 - y1)){
                    y2 = y1;
                }else{
                    x2 = x1;
                }
            }

            Bresenham2.line(x1, y1, x2, y2, (x, y) -> {
                if(mode == 0){
                    //replace
                    painter.drawBlocksReplace(x, y);
                }else{
                    //normal
                    painter.drawBlocks(x, y);
                }
            });
        }
    },
    //Anuke, I don't care. I'm going to make underliquid pubic in my painter and you can't stop me.
    pencil(KeyCode.b, "replace", "square", "drawteams", "underliquid"){
        {
            edit = true;
            draggable = true;
        }

        @Override
        public void touched(int x, int y){
            if(mode == -1){
                //normal mode
                painter.drawBlocks(x, y);
            }else if(mode == 0){
                //replace mode
                painter.drawBlocksReplace(x, y);
            }else if(mode == 1){
                //square mode
                painter.drawBlocks(x, y, true, false, tile -> true);
            }else if(mode == 2){
                //draw teams
                painter.drawCircle(x, y, painter.brushSize, tile -> tile.setTeam(painter.drawTeam));
            }else if(mode == 3){
                painter.drawBlocks(x, y, false, true, tile -> tile.floor().isLiquid);
            }

        }
    },
    eraser(KeyCode.e, "eraseores"){
        {
            edit = true;
            draggable = true;
        }

        @Override
        public void touched(int x, int y){
            painter.drawCircle(x, y, painter.brushSize, data -> {
                if(mode == -1){
                    //erase block
                    data.remove();
                }else if(mode == 0){
                    //erase ore
                    data.clearOverlay();
                }
            });
        }
    },
    fill(KeyCode.g, "replaceall", "fillteams", "fillerase"){
        {
            edit = true;
        }

        IntSeq stack = new IntSeq();

        @Override
        public void touched(int x, int y){
            if(!Structs.inBounds(x, y, painter.width(), painter.height())) return;
            Tile tile = painter.tile(x, y);

            if(tile == null) return;

            if(painter.drawBlock.isMultiblock() && (mode == 0 || mode == -1)){
                //don't fill multiblocks, thanks
                pencil.touched(x, y);
                return;
            }

            //mode 0 or standard, fill everything with the floor/tile or replace it
            if(mode == 0 || mode == -1){
                //can't fill parts or multiblocks
                if(tile.block().isMultiblock()){
                    return;
                }

                Boolf<PaintedTileData> tester;
                Cons<PaintedTileData> setter;

                if(painter.drawBlock.isOverlay()){
                    Block dest = tile.overlay();
                    if(dest == painter.drawBlock) return;
                    tester = t -> t.overlay() == dest && (t.floor().hasSurface() || !t.floor().needsSurface);
                    setter = t -> t.setOverlay(painter.drawBlock);
                }else if(painter.drawBlock.isFloor()){
                    Block dest = tile.floor();
                    if(dest == painter.drawBlock) return;
                    tester = t -> t.floor() == dest;
                    setter = t -> t.setFloorUnder(painter.drawBlock.asFloor());
                }else{
                    Block dest = tile.block();
                    if(dest == painter.drawBlock) return;
                    tester = t -> t.block() == dest;
                    setter = t -> t.setBlock(painter.drawBlock, painter.drawTeam);
                }

                //replace only when the mode is 0 using the specified functions
                fill(x, y, mode == 0, tester, setter);
            }else if(mode == 1){ //mode 1 is team fill

                //only fill synthetic blocks, it's meaningless otherwise
                if(tile.synthetic()){
                    Team dest = tile.team();
                    if(dest == painter.drawTeam) return;
                    fill(x, y, true, t -> t.getTeamID() == dest.id && t.tile.synthetic(), t -> t.setTeam(painter.drawTeam));
                }
            }else if(mode == 2){ //erase mode
                Boolf<PaintedTileData> tester;
                Cons<PaintedTileData> setter;

                if(tile.block() != Blocks.air){
                    Block dest = tile.block();
                    tester = t -> t.block() == dest;
                    setter = t -> t.setBlock(Blocks.air);
                }else if(tile.overlay() != Blocks.air){
                    Block dest = tile.overlay();
                    tester = t -> t.overlay() == dest;
                    setter = t -> t.setOverlay(Blocks.air);
                }else{
                    //trying to erase floor (no)
                    tester = null;
                    setter = null;
                }

                if(setter != null){
                    fill(x, y, false, tester, setter);
                }
            }
        }

        void fill(int x, int y, boolean replace, Boolf<PaintedTileData> tester, Cons<PaintedTileData> filler){
            int width = painter.width(), height = painter.height();

            if(replace){
                //just do it on everything
                for(int cx = 0; cx < width; cx++){
                    for(int cy = 0; cy < height; cy++){
                        PaintedTileData tile = painter.data(cx, cy);
                        if(tester.get(tile)){
                            filler.get(tile);
                        }
                    }
                }

            }else{
                //perform flood fill
                int x1;

                stack.clear();
                stack.add(Point2.pack(x, y));

                try{
                    while(stack.size > 0 && stack.size < width*height){
                        int popped = stack.pop();
                        x = Point2.x(popped);
                        y = Point2.y(popped);

                        x1 = x;
                        while(x1 >= 0 && tester.get(painter.data(x1, y))) x1--;
                        x1++;
                        boolean spanAbove = false, spanBelow = false;
                        while(x1 < width && tester.get(painter.data(x1, y))){
                            filler.get(painter.data(x1, y));

                            if(!spanAbove && y > 0 && tester.get(painter.data(x1, y - 1))){
                                stack.add(Point2.pack(x1, y - 1));
                                spanAbove = true;
                            }else if(spanAbove && !tester.get(painter.data(x1, y - 1))){
                                spanAbove = false;
                            }

                            if(!spanBelow && y < height - 1 && tester.get(painter.data(x1, y + 1))){
                                stack.add(Point2.pack(x1, y + 1));
                                spanBelow = true;
                            }else if(spanBelow && y < height - 1 && !tester.get(painter.data(x1, y + 1))){
                                spanBelow = false;
                            }
                            x1++;
                        }
                    }
                    stack.clear();
                }catch(OutOfMemoryError e){
                    //hack
                    stack = null;
                    System.gc();
                    e.printStackTrace();
                    stack = new IntSeq();
                }
            }
        }
    },
    spray(KeyCode.r, "replace"){
        final double chance = 0.012;

        {
            edit = true;
            draggable = true;
        }

        @Override
        public void touched(int x, int y){

            //floor spray
            if(painter.drawBlock.isFloor()){
                painter.drawCircle(x, y, painter.brushSize, tile -> {
                    if(Mathf.chance(chance)){
                        tile.setFloor(painter.drawBlock.asFloor());
                    }
                });
            }else if(mode == 0){ //replace-only mode, doesn't affect air
                painter.drawBlocks(x, y, tile -> Mathf.chance(chance) && tile.block() != Blocks.air);
            }else{
                painter.drawBlocks(x, y, tile -> Mathf.chance(chance));
            }
        }
    };

    /** All the internal alternate placement modes of this tool. */
    public final String[] altModes;
    /** Key to activate this tool. */
    public KeyCode key = KeyCode.unset; //TODO do I make my terrain painter even accept keybind inputs?
    /** The current alternate placement mode. -1 is the standard mode, no changes.*/
    public int mode = -1;
    /** Whether this tool causes canvas changes when touched.*/
    public boolean edit;
    /** Whether this tool should be dragged across the canvas when the mouse moves.*/
    public boolean draggable;

    PainterTool(){
        this(new String[]{});
    }

    PainterTool(KeyCode code){
        this(new String[]{});
        this.key = code;
    }

    PainterTool(String... altModes){
        this.altModes = altModes;
    }

    PainterTool(KeyCode code, String... altModes){
        this.altModes = altModes;
        this.key = code;
    }

    public void touched(int x, int y){}

    public void touchedLine(int x1, int y1, int x2, int y2){}
}
