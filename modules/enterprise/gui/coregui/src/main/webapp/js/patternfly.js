// PatternFly Namespace
var PatternFly = PatternFly || {};

// Util: PatternFly Popovers
// Add data-close="true" to insert close X icon
(function($) {
  PatternFly.popovers = function( selector ) {
    var allpopovers = $(selector);
    
    // Initialize
    allpopovers.popover();
    
    // Add close icons
    allpopovers.filter('[data-close=true]').each(function(index, element) {
      var $this = $(element),
        title = $this.attr('data-original-title') + '<button type="button" class="close" aria-hidden="true"><span class="pficon pficon-close"></span></button>';

      $this.attr('data-original-title', title);
    });
    
    // Bind Close Icon to Toggle Dispaly
    allpopovers.on('click', function(e) {
      var $this = $(this);
        $title = $this.next('.popover').find('.popover-title');
      
      // Only if data-close is true add class "x" to title for right padding
      $title.find('.close').parent('.popover-title').addClass('closable');
      
      // Bind x icon to close popover
      $title.find('.close').on('click', function() {
        $this.popover('toggle');
      });
      
      // Prevent href="#" page scroll to top
      e.preventDefault();
    });
  };
})(jQuery);