library(ggplot2)
library(ggrepel)
library(directlabels)
library(sitools)
library(dplyr)
library(tikzDevice)


PLOT <- function(fun.data, fun.y, fun.x = "moves", fun.color = "method_heuristic", fun.title="Title", fun.ytrans="identity", fun.xtrans="identity", 
                 fun.ylabels=waiver(), fun.xlabels=waiver(), fun.ylimits=NULL, fun.xlimits=NULL, xlim=NULL, ylim=NULL,
                 fun.bPalette = NULL, fun.mPalette = NULL, legend = FALSE, labels = TRUE) {
  
  fun.data$fun.y     <- fun.data[, fun.y]
  fun.data$fun.x     <- fun.data[, fun.x]
  fun.data$fun.color <- fun.data[, fun.color]
  
  pl = ggplot(fun.data, aes_string(x=fun.x, y=fun.y, color=fun.color)) +
    geom_point(size=0.25) +
    stat_smooth(se=FALSE) +
    scale_y_continuous("",labels=fun.ylabels, trans=fun.ytrans) +
    scale_x_continuous("",labels=fun.xlabels, trans=fun.xtrans) +
    coord_cartesian(xlim=xlim, ylim=ylim) +
    ggtitle(fun.title) + 
    theme_bw() +
    guides(color=guide_legend(override.aes=list(fill=NA))) +
    theme(axis.text.y          = element_text(angle=45),
          axis.text.y          = element_text(angle=45, hjust=1))
  if (labels==TRUE) {
    pl = pl +
      geom_dl(aes(label=fun.color), method=list(dl.trans(x=x+.15),cex=0.6, "last.bumpup"))
  }
  if (legend==TRUE) {
    pl=pl +
    theme(legend.position      = c(0,1),
          legend.justification = c(0,1),
          legend.key           = element_rect(fill = NA, color = NA, size = 0.25))
  } else pl = pl + theme(legend.position="none")
  if (!is.null(fun.bPalette)) { pl = pl + scale_color_brewer(palette = fun.bPalette) }
  if (!is.null(fun.mPalette)) { pl = pl + scale_color_manual(values = fun.mPalette)  }
  pl
}


t3 <- read.table('3x3-celeron.csv',header=TRUE, sep=",",comment="#")
t4 <- read.table('4x4-celeron.csv', header=TRUE,sep=",",comment="#")
o3 <- read.table('3x3-order-dfs.csv', header=TRUE, sep=",", comment="#")
t3$method <- as.factor(paste(as.character(t3$method),as.character(t3$heuristic),sep="-"))
t4$method <- as.factor(paste(as.character(t4$method),as.character(t4$heuristic),sep="-"))
o3$method <- as.factor(paste(as.character(o3$method),as.character(o3$heuristic),sep="-"))


tikz_theme = theme(plot.title=element_text(size=rel(0.8),vjust=0),
                   axis.title=element_text(size=rel(0.8)),
                   axis.title.y=element_text(size=rel(0.8),vjust=2),
                   axis.title.x=element_text(size=rel(0.8),vjust=-0.5),
                   axis.text.x=element_text(size=rel(0.8)),
                   axis.text.y=element_text(size=rel(0.8)),
                   legend.key.size=unit(0.3,"cm"),
                   legend.text=element_text(size=rel(0.6)),
                   legend.title=element_text(size=rel(0.8))
                   )

cbbPalette <- c("#56B4E9","#222222", "#E69F00", "#009E73", "#CC59CC")



tikz(file="figures/fig-2-elapsed.tex",width=3.4,height=3)
PLOT(t4,"time","moves","method","Time (ms) per moves of solution",
     xlim=c(20,52),ylim=c(0,6000),fun.ytrans="sqrt",fun.ylabels=f2si,legend=FALSE) +
     geom_vline(xintercept=47, linetype="dashed") +
     geom_hline(yintercept=0, linetype="dashed") +
     tikz_theme
dev.off()


tikz(file="figures/fig-2-memory.tex",width=3.4,height=3)
PLOT(t4,"memory","moves","method","Memory (bytes) per moves of solution",
     xlim=c(20,52),ylim=c(0,600000000),fun.ytrans="sqrt",fun.ylabels=f2si,legend=FALSE) +
  geom_vline(xintercept=47, linetype="dashed") + 
  geom_hline(yintercept=0, linetype="dashed") +
  tikz_theme
dev.off()

tikz(file="figures/fig-2-expanded.tex", width=3.4, height=3)
PLOT(t4, "expanded", "moves", "method", "Nodes Expanded per moves of solution", 
     xlim=c(20,52),fun.ytrans="sqrt", fun.ylabels=f2si, legend=FALSE) +
  geom_vline(xintercept=47, linetype="dashed") + 
  geom_hline(yintercept=0, linetype="dashed") +
  tikz_theme
dev.off()

tikz(file="figures/fig-2-depth.tex", width=3.4, height=3)
PLOT(t4, "depth", "moves", "method", "Depth of queue per moves of solution", 
     xlim=c(20,52),fun.ytrans="sqrt", fun.ylabels=f2si, legend=FALSE) +
  geom_vline(xintercept=47, linetype="dashed") +
  tikz_theme
dev.off()

tikz(file="figures/fig-1-elapsed.tex",width=3.4,height=3)
PLOT(t3, "time", "moves", "method", "Time (ms) per moves of solution", 
     xlim=c(10,40),ylim=c(0,2000),fun.ytrans="sqrt", fun.ylabels=f2si, legend=TRUE) +
  geom_vline(xintercept=31, linetype="dashed") + 
  geom_hline(yintercept=0, linetype="dashed") +
  tikz_theme
dev.off()
                  
tikz(file="figures/fig-1-memory.tex", width=3.4, height=3)
PLOT(t3, "memory", "moves", "method", "Memory (bytes) per moves of solution", 
  xlim=c(10,40),fun.ytrans="sqrt", fun.ylabels=f2si, legend=TRUE) +
  geom_vline(xintercept=31, linetype="dashed") + 
  geom_hline(yintercept=0, linetype="dashed") +
  tikz_theme
dev.off()

tikz(file="figures/fig-1-expanded.tex", width=3.4, height=3)
PLOT(t3, "expanded", "moves", "method", "Nodes Expanded per moves of solution", 
     xlim=c(10,40),fun.ytrans="sqrt", fun.ylabels=f2si, legend=TRUE) +
     geom_vline(xintercept=31, linetype="dashed") + 
     tikz_theme
dev.off()

tikz(file="figures/fig-1-depth.tex", width=3.4, height=3)
PLOT(t3, "depth", "moves", "method", "Depth of queue per moves of solution", 
     xlim=c(10,40),fun.ytrans="sqrt", fun.ylabels=f2si, legend=TRUE) +
     geom_vline(xintercept=31, linetype="dashed") +
     tikz_theme
dev.off()
                  
tikz(file="figures/fig-3-elapsed.tex",width=3.4,height=3)
PLOT(o3, "time", "moves", "order", "Time (ms) per moves of solution", 
     xlim=c(10,40),fun.ytrans="identity", fun.ylabels=f2si, legend=FALSE) +
  geom_vline(xintercept=31, linetype="dashed") + 
  geom_hline(yintercept=0, linetype="dashed") +
  tikz_theme
dev.off()


                  
