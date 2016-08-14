//
//  LoginViewTabBarController2.h
//  UstadMobileIOS
//
//  Created by Mike Dawson on 11/08/16.
//  Copyright © 2016 UstadMobile FZ-LLC. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "LoginView.h"


@interface LoginViewTabBarController2 : UIViewController <ComUstadmobileCoreViewLoginView>

@property (nonatomic, strong) UIViewController *currentViewController;
@property (nonatomic, weak) IBOutlet UIView *placeholderView;
@property ComUstadmobileCoreControllerLoginController *loginController;

@property (nonatomic, strong) IBOutletCollection(UIButton) NSArray *tabBarButtons;
@property NSString *xapiServer;

@end